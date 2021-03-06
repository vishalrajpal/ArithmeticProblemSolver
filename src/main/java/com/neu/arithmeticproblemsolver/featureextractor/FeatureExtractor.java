package com.neu.arithmeticproblemsolver.featureextractor;

import static com.neu.arithmeticproblemsolver.featureextractor.FeaturesUtilities.dependencyToWord;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.TRAINING_DATA_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.TEST_DATA_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.TRAINING_FEATURES_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.TEST_FEATURES_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_PARENT_INDEX;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_SENTENCE;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_SENTENCES;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_SIMPLIFIED_SENTENCES;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.KEY_SYNTACTIC_PATTERN;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.ADDITION_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.SUBTRACTION_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.QUESTION_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.EQUALS_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.IRRELEVANT_LABEL;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.VERB_FREQUENCY_TRAINING_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.VERB_FEATURES_TRAINING_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.VERB_FREQUENCY_TEST_FILE_PATH;
import static com.neu.arithmeticproblemsolver.featureextractor.PublicKeys.VERB_FEATURES_TEST_FILE_PATH;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.neu.arithmeticproblemsolver.ngramranker.RankerFeatures;
import com.neu.arithmeticproblemsolver.ws4j.VerbSimilarity;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordLemmaTag;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * Extracts the frequencies of n-gram sub pattern features for each label in the dataset.
 */
public class FeatureExtractor {
    
    private static final TreebankLanguagePack TREE_LANGUAGE_PACK;
    private static final MaxentTagger MAXENT_TAGGER;
    private static final DependencyParser DEPENDENCY_PARSER;
    private static final boolean CONSIDER_NGRAM_FEATURES = false;
    private static final boolean CONSIDER_WORD_FREQUENCY_FEATURES = true;
    
    static {
        TREE_LANGUAGE_PACK = new PennTreebankLanguagePack();
        DEPENDENCY_PARSER = DependencyParser.loadFromModelFile("models/english_UD.gz");
        MAXENT_TAGGER = new MaxentTagger("models/english-left3words/english-left3words-distsim.tagger");        
    }

    private final SortedSet<QuestionInstance> mQuestionInstances;
    private final Map<String, Map<String, Integer>> mLabelWordFrequency;
    
    public static void main(final String[] args) { 
    	FeatureExtractor extractor = new FeatureExtractor();
    	extractor.extractFeatures();
    	extractor.printFeatures();
    	//System.out.println(extractor.mQuestionInstances);
    }

    public FeatureExtractor() {
    	mQuestionInstances = new TreeSet<>(getQuestionInstancesComparator());
    	mLabelWordFrequency = new HashMap<>();
    }
    /**
     * Extracts features from the dataset.
     */
    public void extractFeatures() {
        try {
            final InputStream inputFileStream = new FileInputStream(TRAINING_DATA_FILE_PATH);
            final JsonReader jsonReader = Json.createReader(inputFileStream);
            final JsonArray fileArray = jsonReader.readArray();
            final Map<String, Map<String, Integer>> labelToPatternFrequencies = new HashMap<>();
            System.out.println("No of questions:" + fileArray.size());
            int noOfSentencesCount = 0;
            for (int questionCounter = 0; questionCounter < fileArray.size(); questionCounter++) {
            	System.out.println("Question:" + questionCounter);
                final JsonObject questionObject = fileArray.getJsonObject(questionCounter);
                final int questionIndex = questionObject.getInt(KEY_PARENT_INDEX);
                final JsonArray sentences = questionObject.getJsonArray(KEY_SENTENCES);
                final SortedSet<SentenceInstance> sentenceInstances = new TreeSet<>(getSentenceInstancesComparator());
                final int noOfSentences = sentences.size();
                int actualSentenceIndex = 0;
                for (int sentenceCounter = 0; sentenceCounter < noOfSentences; sentenceCounter++) {
                    final JsonObject sentenceObject = sentences.getJsonObject(sentenceCounter);
                    final JsonArray simplifiedSentences = sentenceObject.getJsonArray(KEY_SIMPLIFIED_SENTENCES);
                    final int noOfSimplifiedSentences = simplifiedSentences.size();
                    for (int simpleSentenceCounter = 0; simpleSentenceCounter < noOfSimplifiedSentences; simpleSentenceCounter++) {
                    	
                    	noOfSentencesCount++;
                        final JsonObject simpleSentenceObject = simplifiedSentences.getJsonObject(simpleSentenceCounter);
                        final String simpleSentence = simpleSentenceObject.getString((KEY_SENTENCE));
                        final String label = simpleSentenceObject.getString(KEY_LABEL);
                        final String syntacticPattern = simpleSentenceObject.getString(KEY_SYNTACTIC_PATTERN);

                        final SentenceInstance sentenceInstance = getSentenceInstance(questionIndex, actualSentenceIndex, simpleSentence, syntacticPattern, label);
                        
                        if (sentenceCounter == noOfSentences - 1 && simpleSentenceCounter == noOfSimplifiedSentences - 1) {
                            sentenceInstance.setItALastSentence(true);
                        } else if (actualSentenceIndex == 0){
                        	sentenceInstance.setItAFirstSentence(true);
                        }
                        
                        actualSentenceIndex = actualSentenceIndex + 1;
                        sentenceInstances.add(sentenceInstance);
                        
                        if (CONSIDER_WORD_FREQUENCY_FEATURES) {
                        	getWordFrequency(sentenceInstance, label);
                        }
                        
                        if (CONSIDER_NGRAM_FEATURES) {
                        	generateNGramForSyntacticPattern(label, syntacticPattern, labelToPatternFrequencies);
                        }
                    }
                }

                final QuestionInstance questionInstance = new QuestionInstance(questionIndex, sentenceInstances);
                mQuestionInstances.add(questionInstance);
            }
            
            System.out.println("No of sentences:" + noOfSentencesCount);
            int finalSentenceCount = 0;
            for (final QuestionInstance questionInstance: mQuestionInstances) {				
				for (final SentenceInstance sentenceInstance: questionInstance.getSentenceInstances()){
					finalSentenceCount++;
				}
            }
            System.out.println(finalSentenceCount);
            if (CONSIDER_NGRAM_FEATURES) {
            	getRankedNGrams(labelToPatternFrequencies);
            }
            //printVerbFrequencies();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates the n-grams of the given syntactic pattern and adds it to the nGrams.
     * @param label: The label of the syntactic pattern.
     * @param syntacticPattern: the syntactic pattern.
     * @param nGrams: The n-grams corresponding to each label. 
     */
    private void generateNGramForSyntacticPattern(final String label,
                                       final String syntacticPattern,
                                       Map<String, Map<String, Integer>> nGrams){
        Map<String, Integer> frequency;
        frequency = nGrams.get(label);
        if (frequency == null){
            frequency = new HashMap<String, Integer>();
            nGrams.put(label, frequency);
        }

        for (int charCounter = 0; charCounter < syntacticPattern.length(); charCounter++){
            for (int patternCounter = charCounter + 1; patternCounter < syntacticPattern.length(); patternCounter++){
                String pattern = syntacticPattern.substring(charCounter, patternCounter);
                if (frequency.containsKey(pattern)){
                    frequency.put(pattern, frequency.get(pattern) + 1);
                } else {
                    frequency.put(pattern, 1);
                }
            }
            nGrams.put(label, frequency);
        }
    }
    
    private Set<String> getRankedNGrams(final Map<String, Map<String, Integer>> patternMaps) {
    	final Set<String> rankedNGrams = new HashSet<>();
    	for (int i = 2; i<=20; i++) {
    		System.out.print("Patterns of size:"+i+" "+getPatternsOfKLength(patternMaps, i).size() + "\n");
    	}
    	
    	final Map<String, Integer> patternCounts = new HashMap<>();
    	
    	for(final Map<String, Integer> patternMap: patternMaps.values()) {
    		for(final Entry<String, Integer> patternEntry: patternMap.entrySet()){
    			final String key = patternEntry.getKey();
    			final Integer value = patternEntry.getValue();
    			final int currentCount = patternCounts.containsKey(key) ? patternCounts.get(key) : 0;
    			patternCounts.put(key, currentCount + value);
    		}
    	}
    	
    	final RankerFeatures rankerFeatures = new RankerFeatures(patternCounts);
    	System.out.println(rankerFeatures.getTopNGrams(10));
    	FeaturesUtilities.setTopNGrams(rankerFeatures.getTopNGrams(10));
    	return rankedNGrams;
    }
    
    /**
     * Gets the patterns of length k from the pattern map.
     * @param patternMaps: the patterns with their label.
     * @param k: length of k patterns will be returned.
     * @return k-grams.
     */
    private Set<String> getPatternsOfKLength(final Map<String, Map<String, Integer>> patternMaps, final int k) {
    	final Set<String> kGrams = new HashSet<>();
    	for (Entry<String, Map<String, Integer>> patternMap: patternMaps.entrySet()) {
    		final String label = patternMap.getKey();
    		final Map<String, Integer> patterns = patternMap.getValue();
    		
    		for(final String key: patterns.keySet()) {
    			if (key.length() == k) {
    				kGrams.add(key);
    			}
    		}
    	}
    	return kGrams;
    }

    /**
     * extract the dependency features.
     * @param questionIndex
     * @param simpleSentenceIndex
     * @param simplifiedSentence
     * @return
     */
    private SentenceInstance getSentenceInstance(final int questionIndex, final int simpleSentenceIndex,
    		final String simplifiedSentence, final String syntacticPattern, final String label){
        final Set<FeatureDependency> dependencies = new HashSet<>();

        final StringReader textReader = new StringReader(simplifiedSentence);
        final DocumentPreprocessor textProcessor = new DocumentPreprocessor(textReader);
        textProcessor.setTokenizerFactory(TREE_LANGUAGE_PACK.getTokenizerFactory());

        for (final List<HasWord> sentenceWordList : textProcessor) {
            final List<TaggedWord> taggedWords = MAXENT_TAGGER.tagSentence(sentenceWordList);
            
            final GrammaticalStructure grammaticalStructure = DEPENDENCY_PARSER.predict(taggedWords);
            final Collection<TypedDependency> sentenceDependencies = grammaticalStructure.typedDependenciesCCprocessed();

            for(final TypedDependency sentenceDependency : sentenceDependencies){
                final FeatureDependency currentFeatureDependency = getFeatureDependency(sentenceDependency);
                dependencies.add(currentFeatureDependency);
            }
        }

        return new SentenceInstance(questionIndex,
        				simpleSentenceIndex, 
        				simplifiedSentence, 
        				dependencies, 
        				syntacticPattern,
        				label);
    }

    /**
     *
     * @param simpleSentence
     */
    private void getWordFrequency(final SentenceInstance sentenceInstance, final String label){
        
    	final Set<FeatureDependency> dependencies = sentenceInstance.getFeatureDependencies();
    	final Set<Integer> uniqueVerbIndices = new HashSet<>();
    	
    	if (sentenceInstance.isHasACardinal()) {
    		int cardinalIndex = -1;
    		for (final FeatureDependency featureDependency: dependencies) {			
    			if (featureDependency.getDepTag().equals("CD")) {
    				cardinalIndex = featureDependency.getDepIndex();
    				break;
    			} else if (featureDependency.getGovTag().equals("CD")) {
    				cardinalIndex = featureDependency.getGovIndex();
    				break;
    			} 			
    		}
    		
    		if (cardinalIndex == -1) {
    			System.err.println("Cardinal index must be found");
    		}
    		
    		for (final FeatureDependency featureDependency: dependencies) {
    			if (!uniqueVerbIndices.contains(featureDependency.getDepIndex())
            			&& !Verb.valueOfNullable(featureDependency.getDepTag()).equals(Verb.NONE)
            			&& featureDependency.getDepIndex() < cardinalIndex) {
    				uniqueVerbIndices.add(featureDependency.getDepIndex());
    				final String verb = featureDependency.getDepWord();
        			if (!mLabelWordFrequency.containsKey(verb)) {
        				final Map<String, Integer> labelMap = new HashMap<>();
        				labelMap.put(ADDITION_LABEL, 0);
        				labelMap.put(SUBTRACTION_LABEL, 0);
        				labelMap.put(QUESTION_LABEL, 0);
        				labelMap.put(EQUALS_LABEL, 0);
        				labelMap.put(IRRELEVANT_LABEL, 0);
        				mLabelWordFrequency.put(verb, labelMap); 
        			}
        			final Map<String, Integer> labelMap = mLabelWordFrequency.get(verb);        			
        			if(labelMap.containsKey(label)){
                    	labelMap.put(label, labelMap.get(label) + 1);
                    } else {
                    	labelMap.put(label, 1);
                    }
    			}
    			
    			if (!uniqueVerbIndices.contains(featureDependency.getGovIndex())
            			&& !Verb.valueOfNullable(featureDependency.getGovTag()).equals(Verb.NONE)
            			&& featureDependency.getGovIndex() < cardinalIndex) {
    				uniqueVerbIndices.add(featureDependency.getGovIndex());
    				final String verb = featureDependency.getGovWord();
        			if (!mLabelWordFrequency.containsKey(verb)) {
        				final Map<String, Integer> labelMap = new HashMap<>();
        				labelMap.put(ADDITION_LABEL, 0);
        				labelMap.put(SUBTRACTION_LABEL, 0);
        				labelMap.put(QUESTION_LABEL, 0);
        				labelMap.put(EQUALS_LABEL, 0);
        				labelMap.put(IRRELEVANT_LABEL, 0);
        				mLabelWordFrequency.put(verb, labelMap); 
        			}
        			final Map<String, Integer> labelMap = mLabelWordFrequency.get(verb);        			
        			if(labelMap.containsKey(label)){
                    	labelMap.put(label, labelMap.get(label) + 1);
                    } else {
                    	labelMap.put(label, 1);
                    }
    			}
    		}
    	}
    	
    	
    	/**
    	 * To find Improper labeled Questions.
    	 */
    	/*final String requiredVerb = "spent";
    	final String expectedLabel = "-";*/
    	
       
        			
		/**
    	 * To find Improper labeled Questions.
    	 */
		/*if (word.equals(requiredVerb) && !label.equals(expectedLabel)) {
			System.out.println("Improper Question Index:" + sentenceInstance.getQuestionIndex() + " Label:" + label);
		}*/

		/**
    	 * To find Improper labeled Questions.
    	 */
		/* if (word.equals(requiredVerb) && !label.equals(expectedLabel)) {
			System.out.println("Improper Question Index:" + sentenceInstance.getQuestionIndex() + " Label:" + label);
		}*/
    }

    /**
     * Get the feature dependency from the given TypedDependency.
     * @param sentenceDependency: the typed dependency.
     * @return the feature dependency.
     */
	private FeatureDependency getFeatureDependency(final TypedDependency sentenceDependency) {
		final String relationTag = sentenceDependency.reln().getShortName();
		final String depTag = sentenceDependency.dep().tag() != null ?  sentenceDependency.dep().tag().toString() : "";
		final String govTag = sentenceDependency.gov().tag() != null ?  sentenceDependency.gov().tag().toString() : "";
		final int depIndex = sentenceDependency.dep().index();
		final int govIndex = sentenceDependency.gov().index();
		final String depWord = dependencyToWord(sentenceDependency.dep());
		final String govWord = dependencyToWord(sentenceDependency.gov());
		
		final FeatureDependency currentFeatureDependency = FeatureDependency.builder()
				.relation(relationTag)
				.depTag(depTag)
				.govTag(govTag)
				.depIndex(depIndex)
				.govIndex(govIndex)
				.depWord(depWord)
				.govWord(govWord)
				.build();
		return currentFeatureDependency;
	}
	
	private void printFeatures() {
		try {
			final File features = new File(TRAINING_FEATURES_FILE_PATH);
			final FileWriter fileWriter = new FileWriter(features);
			int noOfQuestions = 0;
			int noOfSentences = 0;
			fileWriter.write(getColumnOrderList());
			for (final QuestionInstance questionInstance: mQuestionInstances) {
				noOfQuestions++;
				for (final SentenceInstance sentenceInstance: questionInstance.getSentenceInstances()){
					noOfSentences++;
					final FeatureVector featureVector = new FeatureVector(sentenceInstance, CONSIDER_NGRAM_FEATURES, CONSIDER_WORD_FREQUENCY_FEATURES);
					fileWriter.write(featureVector.toString());
				}
			}
			fileWriter.flush();
			fileWriter.close();
			System.out.println("No of question:"+noOfQuestions);
			System.out.println("No of sentences:" + noOfSentences);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	public void printVerbFrequencies() {
		try {
			final File verbFrequency = new File(VERB_FREQUENCY_TEST_FILE_PATH);
			final FileWriter frequencyFileWriter = new FileWriter(verbFrequency);
			final StringBuilder sb = new StringBuilder();
			sb.append("Verb,");
			sb.append(ADDITION_LABEL + ",");
			sb.append(SUBTRACTION_LABEL + ",");
			sb.append(QUESTION_LABEL + ",");
			sb.append(EQUALS_LABEL + ",");
			sb.append(IRRELEVANT_LABEL + "\n");
			frequencyFileWriter.write(sb.toString());
			
			final File verbFeatures = new File(VERB_FEATURES_TEST_FILE_PATH);
			final FileWriter featuresFileWriter = new FileWriter(verbFeatures);
			final StringBuilder featuresStringBuilder = new StringBuilder();
			featuresStringBuilder.append("Label,");
			featuresStringBuilder.append("Addition Frequency" + ",");
			featuresStringBuilder.append("Subtraction Frequency" + ",");
			featuresStringBuilder.append("Question Frequency" + ",");
			featuresStringBuilder.append("Equals Frequency" + ",");
			featuresStringBuilder.append("Irrelevant Frequency" + "\n");
			featuresFileWriter.write(featuresStringBuilder.toString());
			
			for (final Entry<String, Map<String, Integer>> verbEntry: mLabelWordFrequency.entrySet()){
				final String verb = verbEntry.getKey();
				final Map<String, Integer> labelMap = verbEntry.getValue();
				final StringBuilder verbFrequencyString = new StringBuilder(verb + ",");
				
				final int additionCount = labelMap.get(ADDITION_LABEL);
				final int subtractionCount = labelMap.get(SUBTRACTION_LABEL);
				final int questionCount = labelMap.get(QUESTION_LABEL);
				final int equalsCount = labelMap.get(EQUALS_LABEL);
				final int irrelevantCount = labelMap.get(IRRELEVANT_LABEL);
				
				verbFrequencyString.append(additionCount + ",");
				verbFrequencyString.append(subtractionCount + ",");
				verbFrequencyString.append(questionCount + ",");
				verbFrequencyString.append(equalsCount + ",");
				verbFrequencyString.append(irrelevantCount + "\n");
				frequencyFileWriter.write(verbFrequencyString.toString());
				
				final float totalVerbCount = additionCount + subtractionCount + questionCount + equalsCount + irrelevantCount;
				final StringBuilder verbFeaturesBuilder = new StringBuilder();
				verbFeaturesBuilder.append(getMaxLabel(labelMap) + ",");
				verbFeaturesBuilder.append(additionCount/totalVerbCount + ",");
				verbFeaturesBuilder.append(subtractionCount/totalVerbCount + ",");
				verbFeaturesBuilder.append(questionCount/totalVerbCount + ",");
				verbFeaturesBuilder.append(equalsCount/totalVerbCount + ",");
				verbFeaturesBuilder.append(irrelevantCount/totalVerbCount + "\n");
				featuresFileWriter.write(verbFeaturesBuilder.toString());
			}
			featuresFileWriter.flush();
			featuresFileWriter.close();
			
			frequencyFileWriter.flush();
			frequencyFileWriter.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	private String getMaxLabel(final Map<String, Integer> labelMap){
		final int additionCount = labelMap.get(ADDITION_LABEL);
		final int subtractionCount = labelMap.get(SUBTRACTION_LABEL);
		final int questionCount = labelMap.get(QUESTION_LABEL);
		final int equalsCount = labelMap.get(EQUALS_LABEL);
		final int irrelevantCount = labelMap.get(IRRELEVANT_LABEL);
		
		int maxCount = additionCount;
		String maxLabel = ADDITION_LABEL;
		
		if (subtractionCount > maxCount){
			maxCount = subtractionCount;
			maxLabel = SUBTRACTION_LABEL;
		}
		if (questionCount > maxCount){
			maxCount = questionCount;
			maxLabel = QUESTION_LABEL;
		}
		if (equalsCount > maxCount){
			maxCount = equalsCount;
			maxLabel = EQUALS_LABEL;
		}
		if (irrelevantCount > maxCount){
			maxCount = irrelevantCount;
			maxLabel = IRRELEVANT_LABEL;
		}
		return maxLabel;
	}
	
    
	private String getColumnOrderList() {
		final StringBuilder columnOrder = new StringBuilder();
		columnOrder.append("Label,");
		columnOrder.append("IsItAFirstSentence,");
		columnOrder.append("IsItALastSentence,");
		columnOrder.append("HasACardinal,");
		columnOrder.append("HasASubject,");
		columnOrder.append("HasAnActionVerb,");
		columnOrder.append("HasADirectObject,");
		columnOrder.append("HasAWHAdverb,");
		columnOrder.append("HasAExpletive,");
		columnOrder.append("HasComparativeAdverb,");
		columnOrder.append("HasSuperlativeAdverb,");
		columnOrder.append("HasPastFormVerb,");
		columnOrder.append("HasBaseFormVerb,");
		
		for (String positiveVerb : VerbSimilarity.POSITIVE_VERBS) {
			columnOrder.append(positiveVerb + ",");
		}
		
		final int negSize = VerbSimilarity.NEGATIVE_VERBS.size();
		for (int verbCount = 0; verbCount < negSize; verbCount++) {
			if (verbCount == negSize - 1) {
				columnOrder.append(VerbSimilarity.NEGATIVE_VERBS.get(verbCount));
			} else {
				columnOrder.append(VerbSimilarity.NEGATIVE_VERBS.get(verbCount) + ",");
			}
		}
		
		columnOrder.append("\n");
		return columnOrder.toString();
	}
	
    /**
     * 
     * @return
     */
    private Comparator<SentenceInstance> getSentenceInstancesComparator() {
    	return new Comparator<SentenceInstance> () {

			@Override
			public int compare(final SentenceInstance o1, final SentenceInstance o2) {
				if (o1.getQuestionIndex() == o2.getQuestionIndex()) {
					return o1.getSentenceIndex() - o2.getSentenceIndex();
				} else {
					return o1.getQuestionIndex() - o2.getQuestionIndex();
				}
			}
    		
    	};
    }
    
    /**
     * 
     * @return
     */
    private Comparator<QuestionInstance> getQuestionInstancesComparator() {
    	return new Comparator<QuestionInstance> () {
			@Override
			public int compare(final QuestionInstance o1, final QuestionInstance o2) {
				return o1.getQuestionIndex() - o2.getQuestionIndex();
			}
    		
    	};
    }
}
