import sqlite3,json,time,datetime
con=sqlite3.connect('compat-cache.db')
con.executescript('PRAGMA user_version=1; CREATE TABLE records(path TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL); CREATE TABLE partitions(name TEXT PRIMARY KEY NOT NULL,boundaries TEXT NOT NULL); CREATE TABLE metadata(key TEXT PRIMARY KEY NOT NULL,value INTEGER NOT NULL);')
now=datetime.datetime.now(datetime.timezone.utc).isoformat()
rows={'players/upgrade_p':{'id':'upgrade_p','name':'Upgrade cache player','active':True,'createdAt':now},'rounds/upgrade_r':{'id':'upgrade_r','type':'hukum','source':'manual','timestamp':now,'playerIds':['upgrade_p'],'hukumId':'upgrade_p','hukumName':'Upgrade cache player','partnerIds':[],'nonPartnerIds':[],'result':'win','bid':155,'sessionId':'upgrade_session','scoreChanges':{'upgrade_p':310}}}
rows['sessions/upgrade_session']={'id':'upgrade_session','sessionName':'Upgrade session','startTime':'2026-10-20T00:00:00Z','endTime':now,'source':'manual'}
con.executemany('INSERT INTO records VALUES(?,?)',[(p,json.dumps(v)) for p,v in rows.items()]);con.executemany('INSERT INTO metadata VALUES(?,?)',[('complete',1),('snapshot_time',int(time.time()*1000))]);
# Match v2.7.20's real integrity metadata, retaining the exact stored row bytes.
import hashlib,struct
h=hashlib.sha256()
def add(value):
 b=value.encode('utf8');h.update(struct.pack('>I',len(b)));h.update(b)
add('tracker-cache-v1:-1:-1')
counts={'players':0,'rounds':0,'sessions':0}
for path,payload in con.execute('SELECT path,payload FROM records ORDER BY path'):
 add(path);add(payload);counts[path.split('/')[0]]+=1
con.executemany('INSERT INTO metadata VALUES(?,?)',[('sync_epoch',-1),('sync_revision',-1),('integrity_version',1),('record_count',len(rows)),('integrity_sha256','sha256:'+h.hexdigest())]+[('count_'+c,n) for c,n in counts.items()])
con.commit();con.close()
