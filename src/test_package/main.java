package test_package;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
/**
 * input : args[0] -> url, args[1]~[끝] -> string
 */
public class main {

    public static void main(String[] args) throws IOException {

        //Jsoup 파싱
//        Document doc = Jsoup.connect("https://en.wikipedia.org/wiki/Korea").get();
//        Elements pTags = doc.getElementsByTag("p");
//        String bodyText = Jsoup.parse(pTags.toString()).text();
        String text = "Korea (officially the \"Korean Peninsula\") is a region in East Asia. Since 1945 it has been divided into the two parts which soon became the two sovereign states: North Korea (officially the \"Democratic People's Republic of Korea\") and South Korea (officially the \"Republic of Korea\"). Korea consists of the Korean Peninsula, Jeju Island, and several minor islands near the peninsula. It is bordered by China to the northwest and Russia to the northeast. It is separated from Japan to the east by the Korea Strait and the Sea of Japan (East Sea). During the first half of the 1st millennium, Korea was divided between the three competing states of Goguryeo, Baekje, and Silla, together known as the Three Kingdoms of Korea.";
        String url = "https://en.wikipedia.org/wiki/";

        List<String[]> triples = text2triple(text);
        triple2rdf(triples,url);

    }

    /**
     * input : text( string )
     * output : triples( List<String[]> )
     */
    public static List<String[]> text2triple (String text) {

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

        //System.out.println(sentList);

        for (String s : sentList) {
            newText += s + " ";
        }
        System.out.println(text);
        System.out.println("--------------------------------------------");
        System.out.println(newText);

        System.out.println("\n \n");

        // openie 파이프라인 설정
        props = PropertiesUtils.asProperties(
                "annotators", "tokenize,ssplit,pos,lemma,parse,natlog,openie"
        );
        props.setProperty("openie.max_entailments_per_clause", "100");
        props.setProperty("openie.triple.strict", "false");
        pipeline = new StanfordCoreNLP(props);

        docu = new Annotation(newText);
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
                String[] statement = {triple.subjectGloss().replaceAll(" ", "-"), triple.relationGloss().replaceAll(" ", "-"), triple.objectGloss().replaceAll(" ", "-")};
                tripleList.add(statement);
            }
            System.out.println("\n");

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
            Resource s = model.createResource(url + statement[0]);
            Property p = model.createProperty("predicate:" + statement[1]);
            RDFNode o = model.createLiteral(url + statement[2]);
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

    /**
     * input : rdf(string)
     * output : 미정
     */
    public static void rdf2kg(String rdf) {

    }

}
