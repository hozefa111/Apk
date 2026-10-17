// Executes HH's real endSession with synthetic in-memory data. No SDK/network initialization.
// node scripts/generate_session_vectors.js /path/to/hh/app.js
const fs=require('fs'),vm=require('vm'),crypto=require('crypto');
const source=fs.readFileSync(process.argv[2],'utf8');
function extract(marker){const start=source.indexOf(marker);if(start<0)throw Error('Missing '+marker);let at=source.indexOf('{',start),depth=1,end=at+1;for(;depth&&end<source.length;end++){if(source[end]==='{')depth++;if(source[end]==='}')depth--;}return source.slice(start,end);}
const functions=['calculateScoreChanges','computeAllPlayerStats','getPlayerName','getWinningStreak','getGameRounds'].map(name=>extract('function '+name+'(')).join('\n')+'\n'+extract('window.endSession = async function(');
const NativeDate=Date,now=Date.parse('2026-10-17T04:00:00Z'),start=Date.parse('2026-10-17T00:00:00Z');
class FixedDate extends NativeDate{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}}
let randomState=71937;function random(n){randomState=(Math.imul(randomState,1664525)+1013904223)>>>0;return randomState%n;}
async function main(){
 const vectors=[];
 for(let fixture=0;fixture<64;fixture++){
  let saved;
  const players=Array.from({length:8},(_,i)=>({id:'p'+i,name:'Player '+i,active:i%3!==0}));
  const sandbox={window:{},Date:FixedDate,players,rounds:[],sessionState:{isActive:true,startTime:new FixedDate(start),activeSessionId:'synthetic',customName:'Synthetic'},
   firebase:{firestore:{FieldValue:{serverTimestamp:()=>new FixedDate(now)},Timestamp:{fromDate:d=>d}}},
   db:{collection:()=>({doc:()=>({update:async data=>{saved=data;}})})},
   showToast:()=>{},showSessionSummaryModal:()=>{},showConfirm:()=>{throw Error('Unexpected empty session');},console:{error:(...args)=>{throw Error(args.join(' '));}}};
  vm.createContext(sandbox);vm.runInContext(functions,sandbox);
  const count=1+fixture%32;
  for(let r=0;r<count;r++){
   const size=3+random(6),ids=players.slice(0,size).map(p=>p.id),hukum=ids[random(size)];
   const others=ids.filter(id=>id!==hukum),partners=others.slice(0,1+random(size-2)),opponents=others.filter(id=>!partners.includes(id));
   const bid=155+5*random(20),result=random(3)?'win':'loss';
   sandbox.rounds.unshift({id:'r'+r,type:'hukum',timestamp:new FixedDate(start+1000*(r+1)),bid,hukumId:hukum,partnerIds:partners,nonPartnerIds:opponents,playerIds:ids,result,scoreChanges:sandbox.calculateScoreChanges(ids,bid,hukum,partners,result)});
  }
  if(fixture%4===0)sandbox.players=players.filter(p=>p.id!=='p0'&&p.id!=='p1');
  if(fixture%5===0)delete sandbox.rounds[0].playerIds;
  sandbox.rounds.unshift({id:'settlement',type:'settlement',timestamp:new FixedDate(start+999999),scoreChanges:{p0:99999}});
  await sandbox.window.endSession();if(!saved)throw Error('No summary for '+fixture);
  const expected={};for(const key of ['totalRounds','leaderboard','mvp','bestHukum','biggestWin','biggestLoss','streaks','badges'])expected[key]=saved[key];
  vectors.push({name:'HH session '+fixture,players:sandbox.players,rounds:sandbox.rounds.filter(r=>r.type!=='settlement'),expected});
 }
 const target=__dirname+'/../app/src/test/resources/web-session-vectors.json';
 fs.writeFileSync(target,JSON.stringify({sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),vectors}));
 console.log(vectors.length+' session vectors from original HH endSession');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
