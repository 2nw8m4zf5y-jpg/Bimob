#!/usr/bin/env python3
import json
from pathlib import Path
p=Path("ai-copilot-contract-v1.2.json")
data=json.loads(p.read_text(encoding="utf-8"))
def inspect(value,path="$"):
    if isinstance(value,str):
        if value != value.strip(): raise SystemExit(f"untrimmed JSON string at {path}: {value!r}")
    elif isinstance(value,list):
        for i,v in enumerate(value): inspect(v,f"{path}[{i}]")
    elif isinstance(value,dict):
        for k,v in value.items(): inspect(k,f"{path}.<key>"); inspect(v,f"{path}.{k}")
inspect(data)
if data.get("$id") != "https://nagaplas.dev": raise SystemExit("incorrect $id")
print("JSON syntax, recursive trimming and $id validation PASS")
