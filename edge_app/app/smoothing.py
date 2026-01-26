from typing import Dict, Any, Optional

from .config import KP_EMA_ENABLE, KP_EMA_ALPHA, KP_MIN_CONF_FOR_SMOOTH

_prev_kp_ema: Optional[Dict[int, Dict[str, float]]] = None

def ema_smooth_keypoints_inplace(obs: Dict[str, Any]) -> Dict[str, Any]:
    global _prev_kp_ema

    if not KP_EMA_ENABLE:
        return obs

    tracks = obs.get("tracks") or []
    if not tracks:
        _prev_kp_ema = None
        return obs

    t0 = tracks[0]

    if not t0.get("has_person", False):
        _prev_kp_ema = None
        return obs

    # raw 우선 사용, 없으면 기존 keypoints를 raw로 간주
    kps_raw = t0.get("keypoints_raw")
    if kps_raw is None:
        kps_raw = t0.get("keypoints") or []
        t0["keypoints_raw"] = kps_raw  # raw로 보존

    if len(kps_raw) == 0:
        _prev_kp_ema = None
        t0["keypoints_smooth"] = []
        return obs

    curr: Dict[int, Dict[str, float]] = {}
    for kp in kps_raw:
        kid = int(kp.get("id"))
        curr[kid] = {
            "x": float(kp.get("x", 0.0)),
            "y": float(kp.get("y", 0.0)),
            "conf": float(kp.get("conf", 0.0)),
        }

    if _prev_kp_ema is None:
        _prev_kp_ema = {kid: {"x": v["x"], "y": v["y"]} for kid, v in curr.items()}
        # 첫 프레임은 smooth=raw
        smooth_list = [{"id": kid, "x": v["x"], "y": v["y"], "conf": v["conf"]} for kid, v in curr.items()]
        smooth_list.sort(key=lambda d: d["id"])
        t0["keypoints_smooth"] = smooth_list
        t0["keypoints"] = smooth_list  # 호환 유지
        t0["quality_score"] = float(sum(k["conf"] for k in smooth_list) / len(smooth_list)) if smooth_list else 0.0
        return obs

    alpha = float(KP_EMA_ALPHA)
    out_list = []

    for kid in sorted(curr.keys()):
        c = curr[kid]
        conf = c["conf"]

        if kid not in _prev_kp_ema:
            _prev_kp_ema[kid] = {"x": c["x"], "y": c["y"]}

        if conf < KP_MIN_CONF_FOR_SMOOTH:
            sx = _prev_kp_ema[kid]["x"]
            sy = _prev_kp_ema[kid]["y"]
        else:
            px = _prev_kp_ema[kid]["x"]
            py = _prev_kp_ema[kid]["y"]
            sx = alpha * px + (1.0 - alpha) * c["x"]
            sy = alpha * py + (1.0 - alpha) * c["y"]
            _prev_kp_ema[kid]["x"] = sx
            _prev_kp_ema[kid]["y"] = sy

        out_list.append({"id": kid, "x": sx, "y": sy, "conf": conf})

    t0["keypoints_smooth"] = out_list
    t0["keypoints"] = out_list  # 기존 로직 호환(스무딩 기준으로 판단하도록)
    t0["quality_score"] = float(sum(k["conf"] for k in out_list) / len(out_list)) if out_list else 0.0
    return obs
