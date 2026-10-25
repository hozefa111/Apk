import sys,re,xml.etree.ElementTree as E
label=sys.argv[1]
for n in E.parse('upgrade-evidence/current.xml').iter('node'):
 if label in (n.get('text'),n.get('content-desc')):
  b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
  if len(b)==4 and b[2]>b[0] and b[3]>b[1]:
   print((b[0]+b[2])//2,(b[1]+b[3])//2);sys.exit(0)
sys.exit(1)
