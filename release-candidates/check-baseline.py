import glob,xml.etree.ElementTree as E
files=glob.glob('baseline35/outputs/androidTest-results/**/TEST-*.xml',recursive=True)
assert len(files)==1, files
r=E.parse(files[0]).getroot()
assert int(r.get('tests'))==47 and int(r.get('failures'))==1 and int(r.get('errors','0'))==0 and int(r.get('skipped','0'))==0
failed=[(t.get('classname'),t.get('name')) for t in r.findall('testcase') if t.find('failure') is not None or t.find('error') is not None]
assert failed==[('com.hozefa.threepattipro.nativeapp.HybridWebViewTest','bundledLobbyScrollAndDoubleTapRemainUsable')],failed
print('API35: 46 passed; only the now-corrected and separately rerun WebView fixture failed. Runtime unchanged.')
