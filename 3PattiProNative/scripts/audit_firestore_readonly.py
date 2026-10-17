#!/usr/bin/env python3
"""GET-only schema/scoring audit. Prints counts, never player identities or tokens."""
import argparse,collections,json,math,urllib.request,urllib.parse
parser=argparse.ArgumentParser();parser.add_argument('--project',required=True);args=parser.parse_args()
if not all(c.isalnum() or c=='-' for c in args.project):raise SystemExit('Invalid project ID')
def decode(value):
    if 'integerValue' in value:return int(value['integerValue'])
    if 'doubleValue' in value:return value['doubleValue']
    if 'arrayValue' in value:return [decode(v) for v in value['arrayValue'].get('values',[])]
    if 'mapValue' in value:return {k:decode(v) for k,v in value['mapValue'].get('fields',{}).items()}
    for key in ('stringValue','booleanValue','timestampValue'): 
        if key in value:return value[key]
    return None
def collection(name):
    rows=[];token=''
    for page in range(20):
        url=f'https://firestore.googleapis.com/v1/projects/{args.project}/databases/(default)/documents/{name}?pageSize=500'
        if token:url+='&pageToken='+urllib.parse.quote(token)
        req=urllib.request.Request(url,method='GET')
        with urllib.request.urlopen(req,timeout=30) as response:data=json.load(response)
        rows.extend({k:decode(v) for k,v in d.get('fields',{}).items()} for d in data.get('documents',[]))
        token=data.get('nextPageToken','')
        if not token:return rows
    raise RuntimeError('Refusing partial paginated audit')
data={name:collection(name) for name in ('players','rounds','sessions')};issues=collections.Counter()
for round in data['rounds']:
    if round.get('type')=='settlement':continue
    try:
        ids=round['playerIds'];bid=round['bid'];hukum=round['hukumId'];partners=round['partnerIds'];result=round['result']
        if len(ids)<3 or len(set(ids))!=len(ids) or hukum not in ids or not partners or hukum in partners or not set(partners).issubset(ids) or len(ids)-len(partners)-1<1:issues['invalid_roles']+=1
        if bid<155 or bid>250 or (bid-155)%5:issues['invalid_bid']+=1
        if result not in ('win','loss'):issues['invalid_result']+=1
        expected={pid:(bid*2 if pid==hukum else bid if pid in partners else 0) if result=='win' else (-bid if pid==hukum else -math.floor(bid/2+0.5) if pid in partners else bid) for pid in ids}
        if expected!=round.get('scoreChanges'):issues['scoring_difference']+=1
    except (KeyError,TypeError,ValueError):issues['invalid_schema']+=1
print(json.dumps({'project':args.project,'counts':{k:len(v) for k,v in data.items()},'issues':dict(issues)},indent=2))
if issues:raise SystemExit(1)
