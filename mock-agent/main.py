import asyncio
import json
import logging
from collections.abc import AsyncIterator

from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.responses import JSONResponse, PlainTextResponse, StreamingResponse
from pydantic import BaseModel, EmailStr

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("mock-agent")

app = FastAPI(title="Gateway Mock Agent", version="1.0.0")

MODES = {"normal", "slow", "http500", "malformed", "disconnect"}


class ChatRequest(BaseModel):
    email: EmailStr
    message: str
    mode: str = "normal"
    delay_seconds: float = 3.0


class GatewayRequest(BaseModel):
    question: str
    user_id: str
    mode: str | None = None
    delay_seconds: float = 3.0


class SystemRequest(BaseModel):
    message: str
    user_id: str
    mode: str | None = None
    delay_seconds: float = 3.0


def resolve_mode(explicit_mode: str | None, message: str) -> str:
    mode = explicit_mode or (message if message in MODES else "normal")
    if mode not in MODES:
        raise HTTPException(status_code=400, detail=f"Unsupported mode: {mode}")
    return mode


async def wait_if_slow(mode: str, delay_seconds: float) -> None:
    if mode == "slow":
        await asyncio.sleep(delay_seconds)


def fail_if_requested(mode: str) -> None:
    if mode == "http500":
        raise HTTPException(status_code=500, detail="Mock Agent HTTP 500")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP", "service": "mock-agent"}


@app.post("/api/chat")
async def chat(body: ChatRequest, mode: str | None = Query(default=None)):
    selected_mode = resolve_mode(mode or body.mode, body.message)
    logger.info("JSON request email=%s mode=%s", body.email, selected_mode)
    fail_if_requested(selected_mode)
    await wait_if_slow(selected_mode, body.delay_seconds)
    if selected_mode == "malformed":
        return PlainTextResponse("not-json", media_type="application/json")
    return {"answer": f"Mock Agent response: {body.message}", "email": str(body.email)}


@app.post("/api/chat/stream")
async def chat_stream(body: ChatRequest, mode: str | None = Query(default=None)):
    selected_mode = resolve_mode(mode or body.mode, body.message)
    logger.info("SSE request email=%s mode=%s", body.email, selected_mode)
    fail_if_requested(selected_mode)

    async def events() -> AsyncIterator[str]:
        await wait_if_slow(selected_mode, body.delay_seconds)
        if selected_mode == "malformed":
            yield "invalid-sse-frame\n\n"
            return
        yield "data: 正在处理\n\n"
        await asyncio.sleep(0.2)
        if selected_mode == "disconnect":
            raise ConnectionResetError("Mock Agent disconnected")
        yield "data: Mock Agent\n\n"
        await asyncio.sleep(0.2)
        yield "data: 返回完成\n\n"

    return StreamingResponse(events(), media_type="text/event-stream")


@app.post("/agent/system/chat")
async def gateway_system_chat(body: SystemRequest):
    selected_mode = resolve_mode(body.mode, body.message)
    logger.info("Gateway system request email=%s mode=%s", body.user_id, selected_mode)
    fail_if_requested(selected_mode)
    await wait_if_slow(selected_mode, body.delay_seconds)
    if selected_mode == "malformed":
        return PlainTextResponse("not-json", media_type="application/json")
    return {"code": "200", "msg": "success", "data": {"answer": f"Mock Agent response: {body.message}"}}


@app.post("/agent/echarts/generate")
@app.post("/agent/analyze")
async def gateway_json_chat(body: GatewayRequest):
    selected_mode = resolve_mode(body.mode, body.question)
    logger.info("Gateway JSON request email=%s mode=%s", body.user_id, selected_mode)
    fail_if_requested(selected_mode)
    await wait_if_slow(selected_mode, body.delay_seconds)
    if selected_mode == "malformed":
        return PlainTextResponse("not-json", media_type="application/json")
    return {"code": 200, "msg": "success", "data": {"answer": f"Mock Agent response: {body.question}"}}


def gateway_event(content: str, done: bool = False) -> str:
    payload = {"content": content, "done": done}
    return f"data:{json.dumps(payload, ensure_ascii=False)}\n\n"


@app.post("/agent/sql/chat")
@app.post("/agent/file/chat")
@app.post("/agent/news/chat")
@app.post("/agent/train/chat")
async def gateway_stream_chat(body: GatewayRequest):
    selected_mode = resolve_mode(body.mode, body.question)
    logger.info("Gateway SSE request email=%s mode=%s", body.user_id, selected_mode)
    fail_if_requested(selected_mode)

    async def events() -> AsyncIterator[str]:
        try:
            await wait_if_slow(selected_mode, body.delay_seconds)
            if selected_mode == "malformed":
                yield "data:not-json\n\n"
                return
            yield gateway_event("正在处理")
            await asyncio.sleep(0.2)
            if selected_mode == "disconnect":
                raise ConnectionResetError("Mock Agent disconnected")
            yield gateway_event("Mock Agent")
            await asyncio.sleep(0.2)
            yield gateway_event("返回完成", done=True)
        except asyncio.CancelledError:
            logger.info("Gateway SSE cancelled email=%s mode=%s", body.user_id, selected_mode)
            raise
        finally:
            logger.info("Gateway SSE closed email=%s mode=%s", body.user_id, selected_mode)

    return StreamingResponse(events(), media_type="text/event-stream")


@app.post("/upload")
async def upload(file: UploadFile = File(...)):
    logger.info("Gateway upload filename=%s", file.filename)
    return {"code": 200, "msg": "success", "data": {"filename": file.filename}}
