# -*- coding: utf-8 -*-
"""
Сбор черт PF2e с pf2.ru в справочник приложения.

Зачем свой разборщик, а не «сходить и пересказать»: страницы черт сервер отдаёт
целиком, и текст правил лежит прямо в HTML. Если прогонять его через пересказ,
на выходе получается не правило, а его изложение — за столом по такому принимают
решения, и это брак. Здесь текст берётся дословно.

Запуск:
    python tools/fetch_feats.py --saved docs/feats tools/feat-urls.txt app/app/src/main/assets/feats.json

С ключом --saved страницы берутся только из папки, куда их сохранили браузером
(Ctrl+S или расширение SingleFile), к сайту не обращаясь. Так и надо: владелец
pf2.ru просит не собирать данные с сайта программами и банит за это. Без ключа
скрипт ходит на сайт сам — это осталось для истории, не запускать.
Страница-список черт (https://pf2.ru/feats) нужна в папке, только чтобы взять
новые черты вкладок и их навыки; без неё вкладки пропускаются, а уже собранное
в справочнике остаётся как есть.

Файл со ссылками — по одной в строке, пустые строки и строки с # пропускаются.
Формат ссылки: https://pf2.ru/feats/<английское имя через %20>?legacy=true

Можно вписать и страницу-список вкладки: https://pf2.ru/feats?tab=skill. Такая
строка разворачивается во все черты вкладки, а у черт навыков заодно
заполняется поле skills — к каким навыкам черта относится.

Уже собранные черты из готового json не перезапрашиваются: список можно дополнять
и запускать снова, лишней нагрузки на сайт не будет.
"""
import html
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "wizards-staff character sheet, personal use"
# Пауза между запросами, секунды: чужой сайт, торопиться некуда. При постоянной
# секунде сайт после полутора сотен страниц начал отвечать 429 «слишком много
# запросов». Каждый раз своя, случайная в этих пределах.
PAUSE_MIN, PAUSE_MAX = 3.0, 7.0
LIMITED_WAIT = 60  # сколько ждать после 429: сколько именно, сайт не сообщает
TIMEOUT_WAIT = 30  # сколько ждать, если сайт не ответил вовсе


# Сохранённые страницы: адрес → файл, None для сохранённых «страница не найдена».
# Пока словаря нет, страницы берутся с сайта; когда есть — только из него.
SAVED = None


class NotSaved(Exception):
    """Страницы нет среди сохранённых."""


def saved_key(url):
    """
    Адрес в вид, по которому сохранённая страница находится независимо от записи:
    %2F и %2f, «?tab=» у списка. Все вкладки списка — одна страница pf2.ru/feats.
    """
    url = urllib.parse.unquote(url).strip().lower()
    return re.sub(r"\?tab=\w+$", "", url)


def load_saved(folder):
    """
    Собирает словарь сохранённых страниц из папки. Адрес страницы браузер пишет
    в начало файла сам: Chrome — «saved from url=(0042)https://…», расширение
    SingleFile — строкой «url: https://…».
    """
    saved = {}
    for name in sorted(os.listdir(folder)):
        if not name.lower().endswith((".html", ".htm")):
            continue
        path = os.path.join(folder, name)
        with open(path, encoding="utf-8", errors="replace") as handle:
            start = handle.read(4000)
        found = re.search(r"saved from url=\(\d+\)(\S+)", start) or re.search(r"^\s*url: (\S+)", start, re.M)
        if not found:
            print("   в файле нет адреса страницы, пропущен:", name)
            continue
        missing = "404 - Ничего не найдено" in start
        saved[saved_key(found.group(1).rstrip(" -"))] = None if missing else path
    print("сохранённых страниц %d, из них «не найдено» %d" % (
        len(saved), sum(1 for path in saved.values() if path is None)))
    return saved


def read_saved(url):
    """
    Сохранённая страница вместо запроса к сайту. SingleFile убирает кавычки
    у атрибутов (class=item-output), а разбор рассчитан на страницу как её
    отдаёт сайт — кавычки возвращаются на место.
    """
    key = saved_key(url)
    if key not in SAVED:
        raise NotSaved(url)
    if SAVED[key] is None:
        raise urllib.error.HTTPError(url, 404, "сохранена страница «не найдено»", {}, None)
    with open(SAVED[key], encoding="utf-8", errors="replace") as handle:
        page = handle.read()
    if "Page saved with SingleFile" not in page[:1000]:
        return page
    quote = lambda tag: re.sub(r"(\s[\w-]+)=([^\s\"'>]+)", r'\1="\2"', tag.group(0))
    return re.sub(r"<[a-zA-Z][^>]*>", quote, page)


def pause():
    """Выдерживает паузу перед следующим запросом к сайту и говорит об этом."""
    if SAVED is not None:
        return  # страницы из папки: сайт не трогаем, ждать нечего
    seconds = random.uniform(PAUSE_MIN, PAUSE_MAX)
    print("   пауза %.1f с" % seconds)
    time.sleep(seconds)


def fetch(url, attempts=3):
    """
    Страница целиком. Сайт иногда не отвечает по полминуты или просит не торопиться
    (429): тогда пробуем ещё раз, не сразу. Ответ 404 не повторяем — такой страницы
    просто нет. С ключом --saved страница берётся только из папки.
    """
    if SAVED is not None:
        return read_saved(url)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == attempts:
                raise
            print("   сайт просит не торопиться, жду %d с" % LIMITED_WAIT)
            time.sleep(LIMITED_WAIT)
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            if attempt == attempts:
                raise
            print("   сайт не ответил (%s), попытка %d из %d" % (error, attempt + 1, attempts))
            time.sleep(TIMEOUT_WAIT)


def strip_tags(fragment):
    """HTML в простой текст: переносы там, где они были, теги прочь."""
    text = re.sub(r"<br\s*/?>", "\n", fragment)
    text = re.sub(r"<hr\s*/?>", "\n\n", text)
    text = re.sub(r"</p>", "\n", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def first(pattern, page, default=""):
    found = re.search(pattern, page, re.S)
    return strip_tags(found.group(1)) if found else default


def parse(page, url):
    """Одна страница черты в запись справочника."""
    head = re.search(r"<h1[^>]*>(.*?)</h1>", page, re.S)
    head_html = head.group(1) if head else ""

    english = first(r'<span class="sr-only" lang="en">(.*?)</span>', head_html)
    # Тип и уровень стоят отдельным значком: «Черта 1».
    kind = first(r'<span class="item-type">(.*?)</span>', head_html)
    # Русское имя — то, что осталось в заголовке, когда убрали служебные значки.
    russian = strip_tags(
        re.sub(r'<span class="(sr-only|action-icon|item-type)".*?</span>', "", head_html, flags=re.S)
    )

    traits = re.findall(
        r'<span class="item-trait[^"]*">(.*?)</span>',
        first_raw(r'<div class="item-traits"[^>]*>(.*?)</div>', page),
        re.S,
    )

    body = first_raw(r'<div class="item-output">(.*?)</div>', page)
    # До первой горизонтальной черты идут источник, требования и прочие пометки,
    # после неё — сам текст правила.
    parts = re.split(r"<hr\s*/?>", body, maxsplit=1)
    header = strip_tags(parts[0]) if parts else ""
    text = strip_tags(parts[1]) if len(parts) > 1 else strip_tags(body)

    level = re.search(r"(\d+)", kind)
    return {
        "en": english,
        "ru": russian,
        "level": int(level.group(1)) if level else None,
        "traits": [strip_tags(trait) for trait in traits],
        "header": header,
        "text": text,
        "url": url,
    }


def first_raw(pattern, page):
    found = re.search(pattern, page, re.S)
    return found.group(1) if found else ""


# Навыки так, как они названы в листе (rules/Pf2.kt, Skill). Сайт местами пишет
# иначе: «Выступление» вместо «Исполнения», «Warfare Знание» вместо «Знания».
SKILLS = [
    "Акробатика", "Мистицизм", "Атлетика", "Ремесло", "Обман", "Дипломатия",
    "Запугивание", "Знание", "Медицина", "Природа", "Оккультизм", "Исполнение",
    "Религия", "Общество", "Скрытность", "Выживание", "Воровство",
]
SKILL_ALIASES = {"Выступление": "Исполнение"}


def find_skills(text):
    """Навыки листа, названные в тексте, в порядке появления."""
    for alias, skill in SKILL_ALIASES.items():
        text = text.replace(alias, skill)
    # Только с начала слова: иначе «Знание» находится в «Распознании заклинаний».
    found = []
    for skill in SKILLS:
        match = re.search(r"\b" + skill, text, re.I)
        if match:
            found.append((match.start(), skill))
    return [skill for position, skill in sorted(found)]


def skills_of(item):
    """
    К каким навыкам относится черта из списка вкладки.

    Сайт указывает навык в поле skill, но у двух десятков черт там прочерк:
    Recognize Spell, Acupuncturist. Тогда навыки берутся из требований.
    У черт на «любой навык» (Assurance, Skill Training) список остаётся пустым.
    """
    skills = find_skills(strip_tags(item.get("skill") or "-"))
    return skills or find_skills(strip_tags(item.get("prerequisites") or ""))


def from_list(url):
    """
    Страница-список вкладки в пары «ссылка на черту — навыки».

    Список лежит в странице JSON-ом, в атрибуте items у <pf2-table>. На странице
    все вкладки сразу, нужная ищется по id из tab= в адресе. Ссылка на черту
    всегда на её легаси-версию: у черт, которые есть только в легаси, адрес
    без ?legacy=true, как и в остальном списке.
    """
    tab = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)["tab"][0]
    # Страница большая, около 700 КБ: сайт отдаёт её до минуты.
    print("загружаю список вкладки %s: %s (может занять до минуты)" % (tab, url))
    page = fetch(url)
    start = page.find('id="%s"' % tab)
    found = re.search(r"<pf2-table\s+items='(.*?)'", page[start:], re.S) if start >= 0 else None
    if not found:
        raise SystemExit("на странице %s нет списка вкладки %s" % (url, tab))
    result = []
    for item in json.loads(html.unescape(found.group(1))):
        link = "https://pf2.ru/feats/" + urllib.parse.quote(item["name"].lower())
        if not item["is_legacy"]:
            link += "?legacy=true"
        result.append((link, skills_of(item) if tab == "skill" else []))
    print("вкладка", tab, "—", len(result), "черт")
    return result


STOP = object()  # знак «сайт ограничил запросы, сбор прекратить»
NOT_SAVED = object()  # знак «с ключом --saved страницы нет в папке»


def take(url, skills):
    """
    Одна черта со страницы: запись справочника, None, если черты нет, STOP,
    если сайт больше не отвечает и продолжать незачем, или NOT_SAVED, если
    с ключом --saved страницы нет в папке. Пауза после запроса — здесь же.
    """
    try:
        page = fetch(url)
    except NotSaved:
        print("   нет среди сохранённых страниц")
        return NOT_SAVED
    except urllib.error.HTTPError as error:
        if error.code != 404:
            # 429 не прошёл и после ожидания или сайт сломался.
            print("   САЙТ ОТВЕТИЛ %s, сбор остановлен" % error.code)
            return STOP
        # Ссылки собираются со страниц классов и народов, и туда попадает не
        # только черта: божество, снаряжение, просто якорь. А у черт из
        # вкладок 404 значит, что легаси-версии у черты нет.
        print("   НЕТ ТАКОЙ СТРАНИЦЫ (404), пропущено")
        pause()
        return None
    except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
        # Одна недоступная страница не повод терять всё собранное: пропускаем,
        # при следующем запуске она запросится снова.
        print("   САЙТ НЕ ОТВЕТИЛ (%s), пропущено" % error)
        return None
    feat = parse(page, url)
    if not feat["en"] or not feat["text"]:
        print("   ПУСТО — страница разобралась не полностью, пропущено")
        pause()
        return None
    print("   взято: %s / %s, уровень %s%s" % (
        feat["ru"], feat["en"], feat["level"],
        ", навыки: " + ", ".join(skills[url]) if url in skills else "",
    ))
    pause()
    return feat


def main():
    global SAVED
    # Каждая строка на экран сразу, а не пачкой в конце: иначе не видно, идёт ли сбор.
    sys.stdout.reconfigure(line_buffering=True)
    args = sys.argv[1:]
    if args[:1] == ["--saved"]:
        SAVED = load_saved(args[1])
        args = args[2:]
    urls_path, out_path = args
    print("сбор черт: список %s, справочник %s, страницы %s" % (
        urls_path, out_path, "из папки" if SAVED is not None else "с сайта"))

    with open(urls_path, encoding="utf-8") as handle:
        lines = [line.strip() for line in handle if line.strip() and not line.startswith("#")]
    # Страницы-списки разворачиваются в ссылки на черты; навыки запоминаются по ссылке.
    raw = []
    skills = {}
    for line in lines:
        if "/feats?" in line:
            if SAVED is not None and saved_key(line) not in SAVED:
                # Список сохраняют не каждый раз. Черты вкладки, уже попавшие
                # в справочник, от этого не пропадут — см. ниже.
                print("страницы-списка %s нет среди сохранённых, вкладка пропущена" % line)
                continue
            for link, feat_skills in from_list(line):
                raw.append(link)
                if feat_skills:
                    skills[link] = feat_skills
            pause()
        else:
            raw.append(line)
    # Одна и та же черта попадает в список дважды: «Блок щитом» есть и среди общих,
    # и среди классовых. Порядок сохраняем, повтор выбрасываем.
    urls = list(dict.fromkeys(raw))
    if len(urls) != len(raw):
        print("в списке", len(raw) - len(urls), "повторов, взяты по одному разу")

    known = {}
    if os.path.exists(out_path):
        with open(out_path, encoding="utf-8") as handle:
            known = {feat["url"]: feat for feat in json.load(handle)}

    todo = [url for url in urls if url not in known]
    print("ссылок %d: уже в справочнике %d, запросить %d" % (len(urls), len(urls) - len(todo), len(todo)))
    print("остановить можно в любой момент через Ctrl+C — собранное запишется")

    # Уже собранные берутся сразу и все, даже если их ссылок в этот раз нет
    # в списке (например, не сохранена страница-список вкладки): справочник
    # только пополняется, молча терять из него черты нельзя.
    feats = list(known.values())
    skipped = []
    not_saved = []
    done = 1
    try:
        for done, url in enumerate(todo, 1):
            print("[%d/%d] %s" % (done, len(todo), url))
            feat = take(url, skills)
            if feat is None:
                skipped.append(url)
                continue
            if feat is NOT_SAVED:
                not_saved.append(url)
                continue
            if feat is STOP:
                # Сайт ограничил запросы: долбить его дальше незачем.
                skipped.extend(todo[done - 1:])
                break
            feats.append(feat)
    except KeyboardInterrupt:
        # Смена VPN посреди сбора: собранное не теряем, остальное — в другой раз.
        print("   остановлено по Ctrl+C")
        skipped.extend(todo[done - 1:])

    # Навыки проставляются и уже собранным чертам: часть черт навыков попала
    # в справочник раньше, чем в нём появилось поле skills.
    for feat in feats:
        feat["skills"] = skills.get(feat["url"], feat.get("skills", []))

    # Разные адреса иногда ведут на одну страницу: «extend armament alignment»
    # и «align armament» — это одна черта, переименованная между редакциями.
    # Считаем чертой пару «английское имя плюс уровень».
    unique = {}
    for feat in feats:
        unique.setdefault((feat["en"], feat["level"]), feat)
    if len(unique) != len(feats):
        print("разных адресов на одну черту:", len(feats) - len(unique))
    feats = sorted(unique.values(), key=lambda feat: feat["ru"].lower())
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(feats, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    print("записано", len(feats), "черт в", out_path)
    if skipped:
        print("пропущено", len(skipped), "ссылок:")
        for url in skipped:
            print("   ", url)

    if not_saved:
        print("нет среди сохранённых страниц", len(not_saved), "ссылок:")
        for url in not_saved:
            print("   ", url)


if __name__ == "__main__":
    main()
