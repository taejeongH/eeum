import asyncio
from .state import MonitorState

PIR_TIMER_SEC = 2 * 60 * 60  # 2 hours

def _cancel(task: asyncio.Task | None):
    if task and not task.done():
        task.cancel()

async def _timer_worker(state: MonitorState):
    try:
        await asyncio.sleep(PIR_TIMER_SEC)
        state.alert = True
        print("[ALERT] No events for 2 hours after PIR!")
    except asyncio.CancelledError:
        pass

def pir_timer_reset(state: MonitorState):
    # 경고는 일단 끄고(새 타이머 시작)
    state.alert = False
    _cancel(state._timer_task)
    state._timer_task = asyncio.create_task(_timer_worker(state))

def pir_timer_off(state: MonitorState):
    _cancel(state._timer_task)
    state._timer_task = None
    state.alert = False

