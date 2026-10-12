"""Inventory source migration references; matching a name is not a behavioral proof."""
import re
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = ROOT / "need_migrate/com/sk89q/worldguard"
NATIVE = ROOT / "src/main/java/com/sk89q/worldguard/neoforge"
sources = {p: p.read_text(encoding="utf-8") for p in NATIVE.rglob("*.java") if "testing" not in p.parts}
mapping=json.loads((ROOT / "tools/migration_mapping.json").read_text(encoding="utf-8"))
registry = ORIGINAL / "protection/flags/Flags.java"
flags = sorted(set(re.findall(r'register\(new \w+(?:<[^>]*>)?\(\s*"([^\"]+)"', registry.read_text(encoding="utf-8"))))
keys = set()
for name in ("bukkit/BukkitWorldConfiguration.java", "config/YamlConfigurationManager.java"):
    contents = (ORIGINAL / name).read_text(encoding="utf-8")
    keys.update(re.findall(r'(?:getBoolean|getString|getInt|getStringList|getTargetMatchers|getProperty)\(\s*"([^\"]+)"', contents))
lines = ["# Сверка с исходным кодом WorldGuard", "", "Автоматический инвентарь имён из `need_migrate`. Упоминание в исходнике указывает место реализации; само по себе оно не доказывает совпадение поведения. Игровые и модульные проверки описаны в MIGRATION.md.", "", f"## Флаги: {len(flags)}", "", "| Флаг оригинала | Нативные файлы |", "| --- | --- |"]
missing_flags = []
for flag in flags:
    files = [p.relative_to(ROOT).as_posix() for p, text in sources.items() if p.name != "FlagRegistry.java" and f'"{flag}"' in text]
    if not files:
        missing_flags.append(flag)
    lines.append(f"| `{flag}` | " + (", ".join(f"[{Path(p).name}]({p})" for p in files) or "Требует ручной проверки") + " |")
lines += ["", f"## Настройки: {len(keys)}", "", "| Ключ оригинала | Нативные файлы |", "| --- | --- |"]
missing_keys = []
for key in sorted(keys):
    files = [p.relative_to(ROOT).as_posix() for p, text in sources.items() if f'"{key}"' in text]
    if not files:
        if key not in mapping["settings"]: missing_keys.append(key)
    lines.append(f"| `{key}` | " + (", ".join(f"[{Path(p).name}]({p})" for p in files) or mapping["settings"].get(key,"Требует ручной проверки")) + " |")
commands=set()
for path in (ORIGINAL / "commands").rglob("*.java"):
    for block in re.findall(r'aliases\s*=\s*\{([^}]+)\}',path.read_text(encoding="utf-8")):
        commands.update(re.findall(r'"([^\"]+)"',block))
lines += ["",f"## Имена и псевдонимы команд: {len(commands)}","","Учитывается регистрация и адаптер старого синтаксиса. Формат вывода и платформенные отличия указаны в MIGRATION.md.","","| Имя оригинала | Нативные файлы |","| --- | --- |"]
missing_commands=[]
for name in sorted(commands):
    files=[p.relative_to(ROOT).as_posix() for p,text in sources.items() if ("command" in p.parts or "admin" in p.parts) and f'"{name}"' in text]
    if not files: missing_commands.append(name)
    lines.append(f"| `{name}` | " + (", ".join(f"[{Path(p).name}]({p})" for p in files) or "Требует ручной проверки") + " |")
lines += ["","## Семейства обработчиков Bukkit","","Это карта замены платформенного слоя, а не доказательство эквивалентности каждого события Bukkit и NeoForge.","","| Исходный обработчик | Замена |","| --- | --- |"]
missing_listeners=[]
for path in sorted((ORIGINAL / "bukkit/listener").glob("*.java")):
    if "@EventHandler" not in path.read_text(encoding="utf-8"): continue
    if path.name not in mapping["listeners"]: missing_listeners.append(path.name)
    lines.append(f"| `{path.name}` | {mapping['listeners'].get(path.name,'Требует ручной проверки')} |")
(ROOT / "MIGRATION_AUDIT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("Original flags:", len(flags), "unmatched references:", missing_flags)
print("Original keys:", len(keys), "unmatched references:", missing_keys)
print("Original command aliases:", len(commands), "unmatched references:",missing_commands)
print("Unmapped listener families:",missing_listeners)
if missing_flags or missing_keys or missing_commands or missing_listeners: raise SystemExit(1)
