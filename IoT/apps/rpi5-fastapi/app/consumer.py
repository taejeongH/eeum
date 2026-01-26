from .state import MonitorState, Event
from .monitor import pir_timer_reset

def _is_pir_motion(ev: Event) -> bool:
    return (
            ev.kind == "pir"
            and ev.data.get("event") == "motion"
            and ev.data.get("value") in (1, True)
    )

async def consume_events(state: MonitorState):
    while True:
        ev = await state.queue.get()

        try:
            if _is_pir_motion(ev):
                state.last_pir_ts = ev.ts
                print(f"[PIR] motion={ev.data.get('value')} from {ev.device} at {ev.ts}")
                pir_timer_reset(state)  # 2시간 타이머 리셋/시작
            else:  # OTHER
                print("[OTHER] event")
        except Exception as e:
            print(f"[consumer] error: {e} (ev={ev})")

