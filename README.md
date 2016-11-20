tcp_connect_forwarder

Create multi-cascading connections via CONNECT proxy with Basic/NTLM authentication

Real examples

@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl 1

Means: 127.0.0.2:21000 > 127.0.0.1:8080 >(ssl)> 33.87.132.11:80 > demo.net:3389 via proxy


@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl 2

Means: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 >(ssl)> demo.net:3389 via proxy


@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80

Means: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 direct port forwarder via proxy

@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -remote 33.87.132.11:80

Means: 127.0.0.2:21000 > 33.87.132.11:80 direct port forwarder


@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -proxy (\j:1)@127.0.0.1:8080 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:80 -subserverNext google.com:443

Means: 127.0.0.2:21000 > 127.0.0.1:8080 > 33.87.132.11:80 > demo.net:80 > google.com:443 via proxy


-proxy and -subserver support Basic or NTLM logons

(\j:1)@ before server:port = NTLM auth

(*\j:bXlsb2dvbjpteXBhc3M=)@ before server:port = NTLM auth, base64 passed as NT hash, NTLMv2 only, NTLMv1 would request extra LM hash. Domain if necessary should follow after * (*mydomain\j:bXlsb2dvbjpteXBhc3M=)

(*\j:bXlsb2dvbjpteXBhc3M=:efOPjfIuWviaV3ZIE7skAQ==)@ before server:port = NTLM, base64:base64 passed as NT:LM hash, used for both NTLMv1 and NTLMv2, but by NTLMv2 only NT hash part get's used, since LM part not involved

(j:1)@ before server:port = Basic auth


PS: if -subserver is given, then -remote server must support CONNECT and -subserver plays the role of final target, if many -subserverNext are given 
then the latest -subserver* plays the role of final target and -remote + all preceding -subserver* must support CONNECT
