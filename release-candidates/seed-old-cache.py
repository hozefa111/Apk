import sqlite3,json,time,datetime
con=sqlite3.connect('compat-cache.db')
con.executescript('PRAGMA user_version=1; CREATE TABLE records(path TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL); CREATE TABLE partitions(name TEXT PRIMARY KEY NOT NULL,boundaries TEXT NOT NULL); CREATE TABLE metadata(key TEXT PRIMARY KEY NOT NULL,value INTEGER NOT NULL);')
now=datetime.datetime.now(datetime.timezone.utc).isoformat()
rows={'players/upgrade_p':{'id':'upgrade_p','name':'Upgrade cache player','active':True,'createdAt':now},'rounds/upgrade_r':{'id':'upgrade_r','type':'hukum','source':'manual','timestamp':now,'playerIds':['upgrade_p'],'hukumId':'upgrade_p','hukumName':'Upgrade cache player','partnerIds':[],'nonPartnerIds':[],'result':'win','bid':155,'scoreChanges':{'upgrade_p':310}}}
con.executemany('INSERT INTO records VALUES(?,?)',[(p,json.dumps(v)) for p,v in rows.items()]);con.executemany('INSERT INTO metadata VALUES(?,?)',[('complete',1),('snapshot_time',int(time.time()*1000))]);con.commit();con.close()
