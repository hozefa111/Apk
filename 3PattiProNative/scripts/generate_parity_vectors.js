// Run with: node scripts/generate_parity_vectors.js /path/to/hh/app.js
// Executes the actual HH scoring function, never Firebase or browser initialization.
const fs = require('fs'), vm = require('vm'), crypto = require('crypto');
const source = fs.readFileSync(process.argv[2], 'utf8');
const name = 'calculateScoreChanges', start = source.indexOf('function ' + name + '(');
const brace = source.indexOf('{', start); let depth = 1, end = brace + 1;
for (; depth && end < source.length; end++) { if (source[end] === '{') depth++; if (source[end] === '}') depth--; }
const sandbox = {}; vm.createContext(sandbox); vm.runInContext(source.slice(start, end), sandbox);
const vectors=[];
for(let size=3; size<=8; size++) for(let count=1;count<size-1;count++) for(let bid=155;bid<=250;bid+=5) for(const result of ['win','loss']) {
 const players=Array.from({length:size},(_,i)=>'p'+i),hukum=players[size-1],partners=players.slice(0,count);
 vectors.push({players,bid,hukum,partners,result,scores:sandbox[name](players,bid,hukum,partners,result)});
}
const target=__dirname+'/../app/src/test/resources/web-scoring-vectors.json';fs.mkdirSync(require('path').dirname(target),{recursive:true});
fs.writeFileSync(target,JSON.stringify({sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),vectors}));
console.log(vectors.length+' vectors from original HH function');
