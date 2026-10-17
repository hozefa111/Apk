#!/usr/bin/env python3
"""Seed synthetic fixtures into demo-three-patti emulators on localhost ONLY."""
import json, urllib.request, urllib.error
AUTH='http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key'
ROOT='http://127.0.0.1:8080/v1/projects/demo-three-patti/databases/(default)/documents'
def request(url,body,method='POST',owner=False):
    headers={'Content-Type':'application/json'}
    if owner:headers['Authorization']='Bearer owner'
    req=urllib.request.Request(url,json.dumps(body).encode(),headers,method=method)
    with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)
def value(v):
    if v is None:return {'nullValue':None}
    if isinstance(v,bool):return {'booleanValue':v}
    if isinstance(v,int):return {'integerValue':str(v)}
    if isinstance(v,list):return {'arrayValue':{'values':[value(i) for i in v]}}
    if isinstance(v,dict):return {'mapValue':{'fields':{k:value(x) for k,x in v.items()}}}
    return {'stringValue':v}
def put(path,data):return request(ROOT+'/'+path,{'fields':{k:value(v) for k,v in data.items()}},'PATCH',True)
try:request(AUTH,{'email':'tester@example.test','password':'test-password-123','returnSecureToken':True})
except urllib.error.HTTPError as error:
    if 'EMAIL_EXISTS' not in error.read().decode():raise
for pid in 'abc':put('players/'+pid,{'name':'Player '+pid,'active':True,'createdAt':'2026-10-17T00:00:00Z'})
put('rounds/fixture-round',{'type':'hukum','timestamp':'2026-10-17T01:00:00Z','bid':155,'hukumId':'a','hukumName':'Player a','partnerIds':['b'],'partnerNames':['Player b'],'nonPartnerIds':['c'],'nonPartnerNames':['Player c'],'playerIds':['a','b','c'],'result':'win','scoreChanges':{'a':310,'b':155,'c':0}})
put('sessions/fixture-session',{'sessionName':'Synthetic Test Session','startTime':'2026-10-17T00:00:00Z','endTime':'2026-10-17T02:00:00Z','totalRounds':1,'duration':7200000,'leaderboard':[{'rank':1,'id':'a','name':'Player a','score':310,'roundsPlayed':1}],'badges':{'mvp':['a']}})
print('Seeded synthetic demo-three-patti emulator fixtures; no production endpoints used.')
