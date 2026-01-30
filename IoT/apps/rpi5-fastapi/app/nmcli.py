import subprocess

def sh(cmd: list[str], check: bool = True):
    print(">", " ".join(cmd))
    return subprocess.run(cmd, check=check, text=True, capture_output=True)
