"""Successor graph of Mebahel's quests: every reference to another quest is a way forward,
except requirements, which point backward."""
import base_quests as b

def _refs(o, skip=False):
    out = []
    if isinstance(o, dict):
        for k, v in o.items():
            if k in ("requires", "required_completed_quests"):
                continue
            out += _refs(v)
    elif isinstance(o, list):
        for x in o:
            out += _refs(x)
    elif isinstance(o, str) and o.startswith("mebahelrpgrquest:"):
        out.append(o.split(":", 1)[1])
    return out

def graph():
    if not b.BASE:
        b.load()
    succ = {k: set() for k in b.BASE}
    for k, (rel, q) in b.BASE.items():
        for r in _refs(q):
            if r in succ and r != k:
                succ[k].add(r)
        for r in q.get("requires", {}).get("quests", []) + q.get("required_completed_quests", []):
            r = r.split(":", 1)[1]
            if r in succ and r != k:
                succ[r].add(k)
    return succ

def chains():
    succ = graph()
    out = {}
    for chain, (g, j, st, dn, bk) in b.LINES.items():
        seen, stack = set(), list(st + bk)
        while stack:
            x = stack.pop()
            if x in seen or x not in b.BASE:
                continue
            seen.add(x)
            stack += succ[x]
        out[chain] = (seen, sorted(x for x in seen if not succ[x]))
    return succ, out
