import re,sys
t=open(sys.argv[1],encoding='utf-8').read()
for sec in t.split('\n## ')[1:]:
    loc=sec.split(':')[0]
    title=re.search(r'\*\*Title:\*\* (.*)',sec).group(1)
    short=re.search(r'\*\*Short description:\*\* (.*)',sec).group(1)
    full=sec.split('**Full description:**\n')[1].split('\n---')[0].strip()
    flag=lambda n,m:'' if n<=m else ' <-- TOO LONG'
    print(f"{loc:8} title {len(title)}{flag(len(title),30)}  short {len(short)}{flag(len(short),80)}  full {len(full)}{flag(len(full),4000)}")
