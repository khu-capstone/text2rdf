package text2rdf;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
/**
 * input : args[0] -> url, args[1]~[끝] -> string
 */
public class Text2rdf {

    private static String CID = "qbQYbo8xjJQzAiQVY471";
    private static String CS = "vE1W_RULUr";

    public static void main(String[] args) throws IOException {

        //Jsoup 파싱
        Document doc = Jsoup.connect("https://en.wikipedia.org/wiki/Korea").get();
        Elements pTags = doc.getElementsByTag("p");
        String bodyText = Jsoup.parse(pTags.toString()).text();
        String text = "Korea (officially the \"Korean Peninsula\") is a region in East Asia. Since 1945 it has been divided into the two parts which soon became the two sovereign states: North Korea's father (officially the \"Democratic People's Republic of Korea\") and South Korea (officially the \"Republic of Korea\"). Korea consists of the Korean Peninsula, Jeju Island, and several minor islands near the peninsula. It is bordered by China to the northwest and Russia to the northeast. It is separated from Japan to the east by the Korea Strait and the Sea of Japan (East Sea). During the first half of the 1st millennium, Korea was divided between the three competing states of Goguryeo, Baekje, and Silla, together known as the Three Kingdoms of Korea.";
        String url = "https://en.wikipedia.org/wiki/";
        String koreanText = "관계 논리(relational calculus)은 관계형 데이터베이스의 관계 모델(관계형 모델)에서 선언적인 방법으로 관계(관계, 테이블, 테이블)로 표현된 데이터 처리, 컴퓨터 과학의 연산 체계이다.";

        List<String[]> triples = text2triple(koreanText);
        triple2rdf(triples,url);

    }

    /**
     * input : text( string )
     * output : triples( List<String[]> )
     */
    public static List<String[]> text2triple (String text) {

        //문장 리스트를 받아옴
        List<String> sentList = getSentList(text);

        // 한국어 문장리스트와 영어문장리스트를 분리함
        List<String> koreanList = new ArrayList<>();
        List<String> englishList = new ArrayList<>();
        for (String data : sentList) {
            int cnt = 0;
            for (int j = 0; j < data.length(); j++) {
                char ch = data.charAt(j);
                if (ch>='가' && ch<='힣') {
                    cnt++;
                }
            }
            if (cnt >= 3){
                koreanList.add(data);
            } else {
                englishList.add(data);
            }
        }


        // 한국어 리스트를 영어로 번역시킴
        List<String> transKoreanList = new ArrayList<>();
        if(koreanList.size() > 0){
            transKoreanList = transK2E(koreanList);
        }


        // 영어리스트를 텍스트로 만들고
        String enText = "";
        for (String s : englishList) {
            enText += s + " ";;
        }
        // 한국어리스트도 텍스트로 만듦
        String koText = "";
        for (String s : transKoreanList) {
            koText += s + " ";;
        }

        // 둘다 대명사 처리해주고
        englishList = getCorefSentList(englishList,enText);
        transKoreanList = getCorefSentList(transKoreanList,koText);

        // 둘다 다시 텍스트로 만들어줌
        enText = "";
        for (String s : englishList) {
            enText += s + " ";
        }
        koText = "";
        for (String s : transKoreanList) {
            koText += s + " ";
        }

        // 둘다 트리플 추출하고
        List<String[]> tripleList = getTripleList(enText);
        List<String[]> koTripleList = getTripleList(koText);

        // 한국어 트리플은 다시 한국어로 변형 시킴
        koTripleList = transE2K(koTripleList);

        // 두 트리플리스트를 합쳐줌
        for (String[] t : koTripleList) {
            tripleList.add(t);
        }

        return tripleList;
    }




    /**
     * input :
     *  - triples(string[n][3])
     *  - url(string)
     * output :
     *  - rdf(string)
     */
    public static String triple2rdf(List<String[]> tripleList, String url) {
        // Jena로 RDF 추출
        Model model = ModelFactory.createDefaultModel();

        for (String[] statement : tripleList) {
            String predic = (statement[1].equals("이즈"))?"는-이다":statement[1].replaceAll(" ", "-");
            Resource s = model.createResource(url + ":" + statement[0].replaceAll(" ", "-"));
            Property p = model.createProperty("predicate:" + predic);
            RDFNode o = model.createLiteral(url + ":" + statement[2].replaceAll(" ", "-"));
            model.add(s,p,o);
        }

        //      파일로 출력
//        File file = new File("./hello.ttl");
//        FileWriter fw = new FileWriter(file);
//
//        RDFDataMgr.write(fw, model, Lang.TTL);

        // 시스템 출력
        RDFDataMgr.write(System.out, model, Lang.TTL);

        //turtle을 String 으로 변환
        String syntax = "TURTLE"; // also try "N-TRIPLE" and "TURTLE"
        StringWriter out = new StringWriter();
        model.write(out, syntax);
        String result = out.toString();

        return result;
    }


    private static List<String> getSentList(String text) {
        // stanford OpenIE
        // 대명사 처리 파이프라인 설정
        Properties props = PropertiesUtils.asProperties(
                "annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref"
        );
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);


        // sentence splitting 으로 문장 나누기
        Annotation docu = new Annotation(text);
        pipeline.annotate(docu);
        List<String> sentList = new ArrayList<>();
        for (CoreMap sentence : docu.get(CoreAnnotations.SentencesAnnotation.class)) {
            sentList.add(sentence.get(CoreAnnotations.TextAnnotation.class));
        }
        return sentList;
    }

    private static List<String> getCorefSentList(List<String> sentList, String text) {
        // stanford OpenIE
        // 대명사 처리 파이프라인 설정
        Properties props = PropertiesUtils.asProperties(
                "annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref"
        );
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Annotation docu = new Annotation(text);
        pipeline.annotate(docu);

        // coref 체인 치환 작업
        String newText = "";
        Collection<CorefChain> values = docu.get(CorefCoreAnnotations.CorefChainAnnotation.class).values();
        for (CorefChain cc : values) {
            //System.out.println("\t" + cc.getMentionsInTextualOrder());
            List<CorefChain.CorefMention> mentionsInTextualOrder = cc.getMentionsInTextualOrder();
            String coreWord = "";
            for (int i = 0; i < mentionsInTextualOrder.size(); i++) {
                if (i == 0) {
                    coreWord = mentionsInTextualOrder.get(i).mentionSpan; // 첫번째 명사를 원래 명사로 지정
                }
                String mention = mentionsInTextualOrder.get(i).mentionSpan; // 대명사 가져오기
                int sentNum = mentionsInTextualOrder.get(i).sentNum - 1; //문장 번호 가져오기
                String modiSent = sentList.get(sentNum); // 수정될 문장 가져오고
                modiSent = modiSent.replaceAll(mention, coreWord); // mention(대명사를) coreWord(원래단어)로 바꿔주고
                sentList.set(sentNum, modiSent); // 수정된 문자열로 바꿔줌
            }
        }

        return sentList;
    }


    private static List<String[]> getTripleList(String text) {
        // openie 파이프라인 설정
        Properties props = PropertiesUtils.asProperties(
                "annotators", "tokenize,ssplit,pos,lemma,parse,natlog,openie"
        );
        props.setProperty("openie.max_entailments_per_clause", "100");
        props.setProperty("openie.triple.strict", "false");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Annotation docu = new Annotation(text);
        pipeline.annotate(docu);
//
        // 트리플 추출
        List<String[]> tripleList = new ArrayList<>();
        int sentNo = 0;
        for (CoreMap sentence : docu.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("Sentence #" + ++sentNo + ": " + sentence.get(CoreAnnotations.TextAnnotation.class));

//          // Print SemanticGraph
//          System.out.println(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class).toString(SemanticGraph.OutputFormat.LIST));

            // Get the OpenIE triples for the sentence
            Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);

            // Print the triples
            for (RelationTriple triple : triples) {
                System.out.println(triple.confidence + "\t" +
                        "<" + triple.subjectGloss() + ">" + "\t" +
                        "<" + triple.relationGloss() + ">" + "\t" +
                        "<" + triple.objectGloss() + ">");
                //String[] statement = {triple.subjectGloss().replaceAll(" ", "-"), triple.relationGloss().replaceAll(" ", "-"), triple.objectGloss().replaceAll(" ", "-")};
                String[] statement = {triple.subjectGloss(),triple.relationGloss(),triple.objectGloss()};
                tripleList.add(statement);
            }
            System.out.println("\n");

        }
        return tripleList;
    }

    private static List<String> transK2E(List<String> koreanList) {

        List<String> transed = new ArrayList<>();
        for (String s : koreanList) {
            transed.add(transrateK2E(s));
        }
        return transed;
    }

    private static List<String[]> transE2K(List<String[]> koTripleList) {
        List<String[]> tripleList = new ArrayList<>();
        for (String[] triple : koTripleList){
            String[] transedTriple = {"","",""};
            for (int i = 0; i<3;i++) {
                transedTriple[i] = transrateE2K(triple[i]);
            }
            tripleList.add(transedTriple);
        }
        return tripleList;
    }

    private static String transrateK2E(String text) {
        String clientId = CID;//애플리케이션 클라이언트 아이디값";
        String clientSecret = CS;//애플리케이션 클라이언트 시크릿값";

        String apiURL = "https://openapi.naver.com/v1/papago/n2mt";

        try {
            text = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("인코딩 실패", e);
        }

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("X-Naver-Client-Id", clientId);
        requestHeaders.put("X-Naver-Client-Secret", clientSecret);

        String responseBody = postK2E(apiURL, requestHeaders, text);
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(responseBody);
            JSONObject message = (JSONObject) jsonObject.get("message");
            JSONObject result = (JSONObject) message.get("result");
            String translatedText = (String) result.get("translatedText");

            return translatedText;
        }catch (ParseException e){
            e.printStackTrace();
            return null;
        }
    }

    private static String postK2E(String apiUrl, Map<String, String> requestHeaders, String text){
        HttpURLConnection con = connect(apiUrl);
        String postParams = "source=ko&target=en&text=" + text; //원본언어: 한국어 (ko) -> 목적언어: 영어 (en)
        try {
            con.setRequestMethod("POST");
            for(Map.Entry<String, String> header :requestHeaders.entrySet()) {
                con.setRequestProperty(header.getKey(), header.getValue());
            }

            con.setDoOutput(true);
            try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(postParams.getBytes());
                wr.flush();
            }

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // 정상 응답
                return readBody(con.getInputStream());
            } else {  // 에러 응답
                return readBody(con.getErrorStream());
            }
        } catch (IOException e) {
            throw new RuntimeException("API 요청과 응답 실패", e);
        } finally {
            con.disconnect();
        }
    }

    private static String transrateE2K(String text) {
        String clientId = CID;//애플리케이션 클라이언트 아이디값";
        String clientSecret = CS;//애플리케이션 클라이언트 시크릿값";

        String apiURL = "https://openapi.naver.com/v1/papago/n2mt";

        try {
            text = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("인코딩 실패", e);
        }

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("X-Naver-Client-Id", clientId);
        requestHeaders.put("X-Naver-Client-Secret", clientSecret);

        String responseBody = postE2K(apiURL, requestHeaders, text);
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(responseBody);
            JSONObject message = (JSONObject) jsonObject.get("message");
            JSONObject result = (JSONObject) message.get("result");
            String translatedText = (String) result.get("translatedText");

            return translatedText;
        }catch (ParseException e){
            e.printStackTrace();
            return null;
        }
    }

    private static String postE2K(String apiUrl, Map<String, String> requestHeaders, String text){
        HttpURLConnection con = connect(apiUrl);
        String postParams = "source=en&target=ko&text=" + text; //원본언어: 영어 (en) -> 목적언어: 한국어 (ko)
        try {
            con.setRequestMethod("POST");
            for(Map.Entry<String, String> header :requestHeaders.entrySet()) {
                con.setRequestProperty(header.getKey(), header.getValue());
            }

            con.setDoOutput(true);
            try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
                wr.write(postParams.getBytes());
                wr.flush();
            }

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // 정상 응답
                return readBody(con.getInputStream());
            } else {  // 에러 응답
                return readBody(con.getErrorStream());
            }
        } catch (IOException e) {
            throw new RuntimeException("API 요청과 응답 실패", e);
        } finally {
            con.disconnect();
        }
    }


    private static HttpURLConnection connect(String apiUrl){
        try {
            URL url = new URL(apiUrl);
            return (HttpURLConnection)url.openConnection();
        } catch (MalformedURLException e) {
            throw new RuntimeException("API URL이 잘못되었습니다. : " + apiUrl, e);
        } catch (IOException e) {
            throw new RuntimeException("연결이 실패했습니다. : " + apiUrl, e);
        }
    }

    private static String readBody(InputStream body){
        InputStreamReader streamReader = new InputStreamReader(body);

        try (BufferedReader lineReader = new BufferedReader(streamReader)) {
            StringBuilder responseBody = new StringBuilder();

            String line;
            while ((line = lineReader.readLine()) != null) {
                responseBody.append(line);
            }

            return responseBody.toString();
        } catch (IOException e) {
            throw new RuntimeException("API 응답을 읽는데 실패했습니다.", e);
        }
    }
}
