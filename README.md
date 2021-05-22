# text2rdf
text를 받아 트리플을 추출하고 추출한 트리플을 바탕으로 rdf를 추출하는 모듈입니다.

# Getting Start
1. Download libraries
    https://drive.google.com/drive/folders/1B0uucnh5J87ayFbKrvWCJ0240ehV9AiP?usp=sharing    
    위 구글 드라이브 링크에서 t2rLib안에 있는 모든 라이브러리를 다운로드합니다.

2. 라이브러리를 프로젝트에 추가
    인텔리J를 쓸경우 Project structure 에서 라이브러리에 lib, lib-src, stanford, json-simple, jsoup를 하나씩 모두 추가해야함

# Method
## text2triple
- **description** : stanford coref를 통해 전처리과정을 거치고 openie로 트리플을 추출합니다.
- **input** : text(String)
- **output** : tripleList(List<String[]>)

## triple2rdf
- **description** : Jena로 트리플의 subject, predicate, object를 모델에 매핑시켜 rdf를 추출합니다. (url은 prefix로 들어감)
- **input** : tripleList(List<String[]>), url(String)
- **output** : rdf(string)
    
# Server
파이썬 서버와 API를 주고 받습니다.
1. 파이썬 input으로 url 들어오면 파싱해서 text와 url을 자바서버로 전달시킴
2. 전달받은 text를 트리플로 변환시켜서 다시 파이썬 서버로 넘김
3. 파이썬 서버에서는 트리플 리스트를 저장하고 트리플 리스트로 Knowledge Graph로 print함
4. 트리플 리스트는 다시 자바서버로 전달돼서 rdf로 변환됨
5. 변환된 rdf는 다시 파이썬 서버로 전송.
> 파이썬 서버에서 원본 text와 url, triple들, rdf파일을 관리함.
