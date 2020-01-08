package fr.insee.eno.postprocessing.fr;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.insee.eno.Constants;
import fr.insee.eno.exception.EnoGenerationException;
import fr.insee.eno.parameters.PostProcessing;
import fr.insee.eno.postprocessing.Postprocessor;
import fr.insee.eno.postprocessing.js.JSVTLParserPostprocessor;

/**
 * Customization of JS postprocessor.
 */
public class FRVTLToXpathParserPostprocessor implements Postprocessor {



	private static final Logger logger = LoggerFactory.getLogger(JSVTLParserPostprocessor.class);


	@Override
	public File process(File input, byte[] parameters, String surveyName) throws Exception {

		File outputCustomFOFile = new File(input.getParent(),
				Constants.BASE_NAME_FORM_FILE +
				Constants.FINAL_JS_EXTENSION);
		logger.info("Start JS parsing xpath to vtl post-processing");

		String inputString = FileUtils.readFileToString(input, StandardCharsets.UTF_8);
		try {
			FileUtils.writeStringToFile(outputCustomFOFile, parseToXpathInNodes(inputString), StandardCharsets.UTF_8);
		}catch(Exception e) {
			String errorMessage = "An error was occured during the " + toString() + " transformation. "+e.getMessage();
			logger.error(errorMessage);
			throw new EnoGenerationException(errorMessage);
		}
		logger.info("End JS parsing xpath to vtl post-processing");

		return outputCustomFOFile;
	}


	public static final String XPATH_CONCAT_FUNCTION = "concat";
	public static final String XPATH_SUBSTRING_FUNCTION = "substring";
	public static final String XPATH_CAST_FUNCTION = "cast";
	public static final String XPATH_DIVISION_FUNCTION = " div ";
	public static final String XPATH_NOT_EQUAL_TO = "!=";

	public static final String VTL_CONCAT_FUNCTION = "||";
	public static final String VTL_SUBSTRING_FUNCTION = "substr";
	public static final String VTL_CAST_FUNCTION = "cast";
	public static final String VTL_DIVISION_FUNCTION = "/";
	public static final String VTL_NOT_EQUAL_TO_XML = "&lt;&gt;";
	public static final String VTL_NOT_EQUAL_TO = "<>";

	public static final String XML_NODE_LABEL = "label";
	public static final String XML_NODE_CONDITIONFILTER = "conditionFilter";
	public static final String XML_NODE_VALUE = "value";
	public static final String XML_NODE_EXPRESSION = "expression";

	public String parseToXpathInNodes(String input) {
		String possibleNodes = "("+XML_NODE_LABEL+"|"+XML_NODE_CONDITIONFILTER+"|"+XML_NODE_VALUE+"|"+XML_NODE_EXPRESSION+")";
		Pattern pattern = Pattern.compile("(<"+possibleNodes+">)((.)*?)(</"+possibleNodes+">)");

		Matcher matcher = pattern.matcher(input);
		StringBuffer stringBuffer = new StringBuffer();
		while(matcher.find()){
			//matcher.group(0)=all expression
			//matcher.group(1)=start of xml node ex:<label>
			//matcher.group(2)=name of xml node ex:label
			//matcher.group(3)=content of xml node
			//matcher.group(4)=last char in group(3) ?
			//matcher.group(5)=end of xml node ex:</label>
			//matcher.group(6)=name of xml node ex:label
			String replacement = matcher.group(1) + parseToXpath(matcher.group(3)) + matcher.group(5);
			matcher.appendReplacement(stringBuffer,"");
			stringBuffer.append(replacement);
		}
		matcher.appendTail(stringBuffer);
		return stringBuffer.toString();
	}



	public String parseToXpath(String input) {
		String finalString="";String context="";
		List<String> listContext = new ArrayList<String>();
		boolean isBetweenRealQuote=false;
		for(int i=0;i<input.length();i++) {
			char c = input.charAt(i);
			context = (c==',') ? "" : context+c;

			if(context.contains(VTL_NOT_EQUAL_TO) && !isBetweenRealQuote) {
				finalString = replaceLast(finalString, VTL_NOT_EQUAL_TO, XPATH_NOT_EQUAL_TO);
			}
			if(context.contains(VTL_NOT_EQUAL_TO_XML) && !isBetweenRealQuote) {
				finalString = replaceLast(finalString, VTL_NOT_EQUAL_TO_XML, XPATH_NOT_EQUAL_TO);
			}
			if(context.contains(VTL_DIVISION_FUNCTION) && !isBetweenRealQuote) {
				finalString = replaceLast(finalString, VTL_DIVISION_FUNCTION, XPATH_DIVISION_FUNCTION);
			}

			switch (c) {
			case '(':
				// order functions by descending length
				if(context.contains(VTL_SUBSTRING_FUNCTION)) {
					finalString = replaceLast(finalString, VTL_SUBSTRING_FUNCTION, XPATH_SUBSTRING_FUNCTION)+c;
					listContext.add(VTL_SUBSTRING_FUNCTION);
				}
				else {
					finalString+=c;
					listContext.add(context.replace("(", ""));
				}
				context="";
				break;
			case '\"':
				if(getLastChar(finalString)!='\\') {
					isBetweenRealQuote=!isBetweenRealQuote;
				}
				finalString+=c;
				break;
			case '|':
				if(getLastChar(finalString) == '|') {
					finalString+=c;
					if(!isBetweenRealQuote) {
						boolean hasContext = getLastElement(listContext)!="";
						String leftOp = hasContext ? replaceLast(context,VTL_CONCAT_FUNCTION, ""):replaceLast(finalString,VTL_CONCAT_FUNCTION, "");
						String originalRightOp = findRightOp(input, i+1);
						String rightOp = parseToXpath(findRightOp(input, i+1));
						finalString = replaceLast(finalString,context, "");
						finalString = hasContext ? finalString+concatFunction(leftOp,rightOp) : concatFunction(leftOp,rightOp) ;
						i+=originalRightOp.length();
					}
				}
				else {
					finalString+=c;
				}
				break;
			case ')':
				finalString+=c;
				removeLast(listContext);
				context="";
				break;
			default:
				finalString+=c;
				break;
			}
		}
		return finalString;
	}

	public String concatFunction(String leftOp, String rightOp) {
		return XPATH_CONCAT_FUNCTION+"("+leftOp+","+rightOp+")";
	}

	public String findRightOp(String input, int index) {
		input = input.replaceAll("&lt;&gt;", "<>");
		List<String> listContext = new ArrayList<String>();
		String specialChar = ",=+-*/<>";
		String rigthOp = "";
		String context = "";
		for(int i=index;i<input.length();i++) {
			char c = input.charAt(i);
			context = (c==',') ? "" : context+c;
			
			if(c=='(') {
				listContext.add(context.replace("(", ""));
				context="";
				rigthOp+=c;
			}
			else if(c==')') {
				removeLast(listContext);
				context="";
				rigthOp+=c;
			}
			else if(specialChar.contains(c+"")) {
				if(listContext.size()!=0) {
					rigthOp+=c;
				}
				else {
					return rigthOp;
				}
			}
			else {
				rigthOp+=c;
			}
		}
		return rigthOp;
	}
	

	public char getLastChar(String text) {
		return text.isEmpty() ? 0: text.charAt(text.length()-1);
	}

	public String getLastElement(List<String> contexts) {
		return contexts.isEmpty() ?"": contexts.get(contexts.size()-1);
	}

	public void removeLast(List<String> contexts) {
		if (!contexts.isEmpty()) {
			contexts.remove(contexts.size()-1);
		}
	}

	public String replaceLast(String string, String substring, String replacement){
		int index = string.lastIndexOf(substring);
		if (index == -1)
			return string;
		return string.substring(0, index) + replacement + string.substring(index+substring.length());
	}

	public String toString() {
		return PostProcessing.JS_VTL_PARSER.name();
	}

}
