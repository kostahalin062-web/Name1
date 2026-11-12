"""Задаёт относительные ширины колонок pipe-таблиц Markdown по заголовку. usage: table_widths.py file.md"""
import sys, re
W = {('Термин','Определение'):(1,3), ('Зона','Количество мест','Характеристика'):(2,1,5),
     ('Должность','Численность','Роль в Системе'):(3,1,2), ('№','Подсистема','Обозначение'):(1,8,3),
     ('Код','Функция','Роль'):(1,6,2), ('Сущность','Ключевые атрибуты'):(1,3),
     ('Этап','Содержание','Результат','Срок'):(3,4,3,2)}
lines = open(sys.argv[1], encoding='utf-8').read().split('\n')
for i in range(len(lines)-1):
    if lines[i].startswith('|') and re.match(r'^\|[\s:\-|]+\|$', lines[i+1]):
        hdr = tuple(c.strip() for c in lines[i].strip('|').split('|'))
        ratio = W.get(hdr) or (1,)*len(hdr)
        if len(ratio) == len(hdr):
            lines[i+1] = '|' + '|'.join('-'*(r*8) for r in ratio) + '|'
open(sys.argv[1], 'w', encoding='utf-8').write('\n'.join(lines))
