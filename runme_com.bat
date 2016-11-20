@set path=C:\Program Files\Java\jdk1.8.0_45\bin;%path%
@set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_45


@rem java -version
@java -cp "%~dp0localwebs.jar" juwagn.ServerListener -local 127.0.0.2:21000 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl yes

@rem java -cp "%~dp0localwebs.jar" juwagn.ServerListener -proxy (\j:1)@127.0.0.1:8080 -local 127.0.0.2:21000 -remote 33.87.132.11:80 -subserver (test:ok)@demo.net:3389 -ssl yes
@pause
@exit

Possible run parameters: 

-local 127.0.0.2:21000
(the server listener, by 0.0.0.0:21000 it will listen on all interfaces)

-remote 33.87.132.11:80
(if -subserver given, remote server must support http connect proxy else will just forward traffic as is)

-ssl yes or -ssl 1
(ssl will be established before creating cascading subserver connection)
-ssl 2
(ssl will be established after creating cascading subserver connection, if -subserver not given equals -ssl 1)

-user_agent 
(set own user agent of any browser)

-subserver 10.10.8.205:3389
or
-subserver 10.10.8.205:80 -subserverNext 10.10.8.201:3389
(it means multi-cascading via http connect proxy)
or
-subserver bXlsb2dvbjpteXBhc3M=@10.10.8.205:3389 -subserverNext bXlsb2dvbjpteXBhc3M=@10.10.8.201:3389
(bXlsb2dvbjpteXBhc3M means base64 ut8 encoded Basic or NTLM authentication realm in this example Basic> mylogon:mypass before forwarding connection to target)
or
-subserver (mylogon:mypass)@10.10.8.205:3389 -subserverNext (mylogon:mypass)@10.10.8.201:3389
(same as before, but auth data gets encoded automatically, in this example mylogon:mypass)
(if -subserver not given it will act as direct port forwarder)

-proxy your.proxy.com:8080
or
-proxy bXlsb2dvbjpteXBhc3M=@your.proxy.com:8080
(proxy or proxy with Basic or NTLM authentication realm base64 ut8 encoded)
or
-proxy (mylogon:mypass)@your.proxy.com:8080
(same as before, but auth data gets encoded automatically, in this example mylogon:mypass)
or
-proxy (Domain/mylogon:mypass)@your.proxy.com:8080
(ntlm based logon with domain)
or
-proxy (/mylogon:mypass)@your.proxy.com:8080
(ntlm based logon without domain)
(the presence of / slash char means always NTLM logon)

-ntlm_hash mypass
(displays computed NT and LM hashes of passed password to reuse for NTLM auth instead clear password, check below how to reuse it)
------------------------------------------------------------

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