package colovo;

import java.io.*;

public class LexanCSV {

/*
 * class LexanCSV
 * 
 * 2011-12-04 11:04 Jim Peterson jimpeterson0462@outlook.com
 * 
 * 		This is a simple lexical analyzer for common flat files ( csv files ).

 * 		*** TESTING and Proper completion *** 2ar6
 *
 * 		NOTE: I have high confidence that this is working well.
 * 			I have tested combinations of Quote Chars, Comma and Tab Separators, ignore White Space, ignore line ends, The four types of line ends,
 * 			the correctness of the line numbers and positions, interpretation of white space, Leading whitespace, trailing white space, leading delimiter,
 * 			trailing delimiter, empty lines, other non-printable/extended characters, embedded escape chars, larger pipe size, multiple Field Separators and Quotes,
 * 			assignable FieldSeparators, assignable Quotes, assignable white space.
 * 
 * 		NOTE: I like the idea of Sub-States by using another variable with lxState like lxEOLState. I wonder if I can make this more generic.
 * 
 * 		*** Cool new improvements ***
 * 
 * 		TODO: For the options, it would be great to be able to add and remove quotes, fields separators, etc with having to recreate the entire string
 * 
 * 		TODO: Do I need to return or indicate what the quote char is for each string?
 * 
 * 		TODO: Do I need better comments and documentation? What about JavaDoc?
 * 
 * 		TODO: Should I make a recognizer for Numbers and Words or allow them to just be 'Other' for now?
 * 
 * 		TODO: I haven't tested this with IOErrors. I tested 'File Not Found'. I can't think of any
 * 				other errors that are not system or hardware error which are all outside of the
 * 				scope of this module.
 * 
 * 		TODO: I wonder if I should do some exception handling. No. Just throwing the errors is fine.
 * 
 * 	 	TODO: I would like to reduce the pipe size to one or better yet use just a single char.
 * 				Just keep it for now. It works fine and I may change my mind to use it.
 * 
 * 		TODO: How about a standard unit/integration/regression test!
 * 
 * 		TODO: I 'm not going to deal with it now but, I'd like to try it with a Unicode File.
 * 
 * 		TODO: Keep the whole line for error reporting.
 * 
 * 		TODO: We can get the line/pos of the beginning of the lexeme. Would it be of value to get the current line/pos?
 * 
 * 		TODO: keep the lexeme separate from the token value
 * 
 * 		TODO: How about options for not interpreting escape chars and for converting non-print able chars to escapes
 * 
 * 		TODO: more escape types octal, hex, unicode, HTML codes like &amp;
 * 
 * 		TODO: How about Grouping Begin/End pairs. For cvs files you would presume a Group Begin at the beginning of the file and interpret line ends as a GroupEnd/GroupBegin pair.
 * 
 * 		TODO: I would like much more generic ways to denote whitespace, delimiters, line ends etc. Like maybe regular expressions.
 * 
 * 		TODO: I would like to take the loop body and put it in a function that gets called in the loop. This leads the way for adapting it for more generic purposes.
 * 
 * 		TODO: (advanced) I want to have multiple recognizers running on the same stream.
 * 
 * 		TODO: I think it might be good to change how advance() is used so that characters can be feed into the recognizer rather than the recognizer fetching them.
 * 
 */

	//- - - - - - - - - - - - - - - - - - - - - - - -
	static final int pipeSize = 2;
	static final int C_loopFailSafe = 10000; // Basically the maximum size of any one token.

	//- - - - - - - - - - - - - - - - - - - - - - - -
	public enum LexToken {
		tokenEnd				("End"),
		tokenNone				("None"),
		tokenWhitespace			("Whitespace"),
		tokenFieldSeparator		("FieldSeparator"),
		tokenLineSeperator		("LineSeparator"),
		tokenWord				("Word"),
		tokenNumber				("Number"),
		tokenString				("String"),
		tokenOther				("Other"),
		tokenTerminusUltima		("TerminusUltima");
		
		private final String name;
		private LexToken(String pName) {
			this.name = pName;
		}
	}

	//- - - - - - - - - - - - - - - - - - - - - - - -
	private enum LexState {
		lxstBEG,
		lxstEND,
		lxstWhitespace,
		lxstFieldSeparator,
		lxstLineSeparator,
		lxstEndOfIntput,
		lxstOther,

		//- - - - - - - - - -
		lxstQuoteBeg,
		lxstQuoteTxt,
		lxstQuoteEsc,
		lxstQuoteEscVal,
		lxstQuoteEnd,

		//- - - - - - - - - -
		lxstEolCh,
		lxstEolCR,
		lxstEolLF,
		lxstEolCRLF,
		lxstEolLFCR,

		//- - - - - - - - - -
		lxstTerminusUltima
	}
	
	//- - - - - - - - - - - - - - - - - - - - - - - -
	private Reader		cvInReader;	
	private char		cvCharPipe[];
	private int			cvLineNo;
	private int			cvLinePos;
	private int			cvTokenLineNo;
	private int			cvTokenLinePos;
	private char		cvLexQuoteChar;
	private String		cvLexeme;
	private LexState	cvState;
	private LexState	cvEolStateLC;
	private LexState	cvEolState;
	private LexToken	cvToken;

	//- - - - - - - - - - - - - - - - - - - - - - - -
	private boolean 	cvIgnoreWhiteSpace		= false;
	private boolean 	cvIgnoreLineEnds		= false;
	private boolean 	cvEmbedDoubleQuotes		= true;
	private char		cvEscapeChar 			= '\\';
	private String		cvFieldSeparators 		= ",";
	private String		cvLineSeparators 		= "\r\n";
	private String		cvQuotes 				= "\"'";;
	private boolean 	cvUnterminatedString	= false;
	private String  	cvWhiteSpace			= "\0\1\2\3\4\5\6\7\10\11\12\13\14\15\16\17\20\21\22\23\24\25\26\27\30\31\32\33\34\35\36\37\40";

	//========================================================================================================
	private void advance() throws IOException {

		//- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
		// Advance the pipeline and get the next char from the stream
		//
		for ( int i = 1; i < pipeSize; i++ ) cvCharPipe[i-1] = cvCharPipe[i];
 		if ( cvInReader.read( cvCharPipe, pipeSize-1, 1 ) == -1 ) cvCharPipe[pipeSize-1] = '\uffff';

		//- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
		// Track the line and column position even if EOL is ignored,
		// regarded as white space, or embedded in a string.
		// This will interpret CR, LF, CR/LF and LF/CR as line endings.
		switch (cvEolStateLC) {
		case lxstEolCh:
			switch (cvCharPipe[0]) {
			case '\r':
				cvEolStateLC = LexState.lxstEolCR;
				break;
			case '\n':
				cvEolStateLC = LexState.lxstEolLF;
				break;
			}
			break;

		case lxstEolCR:
			switch (cvCharPipe[0]) {
			case '\r':
	 			cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolCR;
				break;
			case '\n':
				cvEolStateLC = LexState.lxstEolCRLF;
				break;
			default:
	 			cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolCh;
				break;
			}
			break;

		case lxstEolLF:
			switch (cvCharPipe[0]) {
			case '\r':
				cvEolStateLC = LexState.lxstEolLFCR;
				break;
			case '\n':
				cvLinePos = 0;
				cvLineNo ++;
				cvEolStateLC = LexState.lxstEolLF;
				break;
			default:
				cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolCh;
				break;
			}
			break;

		case lxstEolLFCR:
		case lxstEolCRLF:
			switch (cvCharPipe[0]) {
			case '\r':
	 			cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolCR;
				break;
			case '\n':
	 			cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolLF;
				break;
			default:
	 			cvLinePos = 0;
	 			cvLineNo ++;
	 			cvEolStateLC = LexState.lxstEolCh;
				break;
			}
			break;

		}
		
 		cvLinePos++;

	}

	//========================================================================================================
	public LexanCSV (Reader ir) throws IOException {

		cvInReader = ir;

		cvCharPipe = new char[pipeSize];
		for ( int i = 0; i < pipeSize; i++) cvCharPipe[i] = '\0';

		cvState 		=	LexState.lxstBEG;
		cvEolState 		=	LexState.lxstEolCh;
		cvEolStateLC	=   LexState.lxstEolCh;

		for ( int i = 0; i < pipeSize; i++) advance();

		cvLineNo 	= 1;
		cvLinePos	= 1;

	}
	

	//========================================================================================================
	private boolean isFieldSeparator ( char cc ) {
		return (cvFieldSeparators.indexOf(cc) != -1);
	}

	//========================================================================================================
	private boolean isLineSeparator ( char cc ) {
		return ( ! cvIgnoreLineEnds && cvLineSeparators.indexOf(cc) != -1 );
	}

	//========================================================================================================
	private boolean isWhitespace ( char cc ) {
		return ( cvWhiteSpace.indexOf(cc) != -1 && ! isFieldSeparator(cc) && ! isLineSeparator(cc) );
	}

	//========================================================================================================
	private boolean isEnd ( char cc ) {
		return ( cc == '\uffff' );
	}

	//========================================================================================================
	private boolean isQuote ( char cc ) {
		return ( cvQuotes.indexOf(cc) != -1 );
	}

	//========================================================================================================
	public int getLineNo() {
		return cvTokenLineNo;
	}
	
	//========================================================================================================
	public int getLinePos() {
		return cvTokenLinePos;
	}
	
	//========================================================================================================
	public LexToken getToken() {
		return cvToken;
	}
	
	//========================================================================================================
	public String getLexeme() {
		return cvLexeme;
	}
	
	//========================================================================================================
	public String getTokenName() {
		return cvToken.name;
	}

	//========================================================================================================
	public boolean isIgnoreWhiteSpace() {
		return cvIgnoreWhiteSpace;
	}

	//========================================================================================================
	public void setIgnoreWhiteSpace(boolean ignoreWhiteSpace) {
		cvIgnoreWhiteSpace = ignoreWhiteSpace;
	}

	//========================================================================================================
	public boolean isIgnoreLineEnds() {
		return cvIgnoreLineEnds;
	}

	//========================================================================================================
	public void setIgnoreLineEnds(boolean ignoreLineEnds) {
		cvIgnoreLineEnds = ignoreLineEnds;
	}

	//========================================================================================================
	public boolean isEmbedDoubleQuotes() {
		return cvEmbedDoubleQuotes;
	}

	//========================================================================================================
	public void setEmbedDoubleQuotes(boolean embedDoubleQuotes) {
		cvEmbedDoubleQuotes = embedDoubleQuotes;
	}

	//========================================================================================================
	public char getEscapeChar() {
		return cvEscapeChar;
	}

	//========================================================================================================
	public void setEscapeChar(char escapeChar) {
		cvEscapeChar = escapeChar;
	}

	//========================================================================================================
	public String getFieldSeparators() {
		return cvFieldSeparators;
	}

	//========================================================================================================
	public void setFieldSeparators(String fieldSeparators) {
		cvFieldSeparators = fieldSeparators;
	}

	//========================================================================================================
	public String getLineSeparators() {
		return cvLineSeparators;
	}

	//========================================================================================================
	public void setLineSeparators(String lineSeparators) {
		cvLineSeparators = lineSeparators;
	}

	//========================================================================================================
	public String getQuotes() {
		return cvQuotes;
	}

	//========================================================================================================
	public void setQuotes(String quotes) {
		cvQuotes = quotes;
	}

	//========================================================================================================
	public boolean isUnterminatedString() {
		return cvUnterminatedString;
	}

	//========================================================================================================
	public String getWhiteSpace() {
		return cvWhiteSpace;
	}

	//========================================================================================================
	public void setWhiteSpace(String whiteSpace) {
		cvWhiteSpace = whiteSpace;
	}

	//========================================================================================================
	public LexToken next() throws IOException {

		int loopFailsafe = C_loopFailSafe;

		cvLexeme = "";
		cvToken  = LexToken.tokenNone;
		cvUnterminatedString = false;
		cvState = LexState.lxstBEG;
		cvEolState = LexState.lxstEolCh;

		lexLoop:
		do {
		
			if (--loopFailsafe <= 0) { break lexLoop; }
		
			switch (cvState) {
			
			//-----------------------------------------------------
			// Beginning
			//
			case lxstBEG:
			
				cvTokenLineNo = cvLineNo;
				cvTokenLinePos = cvLinePos;
				
				if ( isWhitespace(cvCharPipe[0])) {
					cvState = LexState.lxstWhitespace;
				}

				else if ( isFieldSeparator(cvCharPipe[0])) {
					cvState = LexState.lxstFieldSeparator;
				}

				else if ( isLineSeparator(cvCharPipe[0])) {
					cvState = LexState.lxstLineSeparator;
				}

				else if ( isQuote(cvCharPipe[0])) {
					cvState = LexState.lxstQuoteBeg;
				}

				else if ( isEnd(cvCharPipe[0])) {
					cvState = LexState.lxstEndOfIntput;
				}

				else {
					cvState = LexState.lxstOther;
				}

				break;
			
			//-----------------------------------------------------
			// Other
			//
			case lxstOther:
				if ( 	isWhitespace(cvCharPipe[0])
					|| 	isFieldSeparator(cvCharPipe[0])
					||	isLineSeparator(cvCharPipe[0])
					||  isQuote(cvCharPipe[0])
					||  isEnd(cvCharPipe[0])  ) {
					cvToken = LexToken.tokenOther;
					cvState = LexState.lxstEND;
				}
				else {
					cvLexeme += cvCharPipe[0];
					advance();
					cvState = LexState.lxstOther;
				}
				break;

			//-----------------------------------------------------
			// Whitespace
			//
			case lxstWhitespace:
				if ( isWhitespace(cvCharPipe[0])) {
					if ( !cvIgnoreWhiteSpace ) {
						cvLexeme += cvCharPipe[0];
					}
					advance();
					cvState = LexState.lxstWhitespace;
				}
				else {
					if ( cvIgnoreWhiteSpace ) {
						cvState = LexState.lxstBEG;
					}
					else {
						cvToken = LexToken.tokenWhitespace;
						cvState = LexState.lxstEND;
					}
				}
				break;

			//-----------------------------------------------------
			// Quote String
			//
			case lxstQuoteBeg:
				cvLexQuoteChar = cvCharPipe[0];
				advance();
				cvState = LexState.lxstQuoteTxt;
				break;

			case lxstQuoteTxt:
				if (cvCharPipe[0] == cvLexQuoteChar ) {
					cvState = LexState.lxstQuoteEnd;
					advance();
				}
				else if ( cvCharPipe[0] == cvEscapeChar ) {
					advance();
					cvState = LexState.lxstQuoteEsc;
				}
				else if (cvCharPipe[0] == '\uffff') {
					cvUnterminatedString = true;
					cvToken = LexToken.tokenString;
					cvState = LexState.lxstEND;
				}
				else {
					cvLexeme += cvCharPipe[0];
					advance();
					cvState = LexState.lxstQuoteTxt;
				}
				break;
				
			case lxstQuoteEsc:
				switch (cvCharPipe[0]) {
				case 't':
					cvLexeme += '\t';
					advance();
					cvState = LexState.lxstQuoteTxt;
					break;
				case 'n':
					cvLexeme += '\n';
					advance();
					cvState = LexState.lxstQuoteTxt;
					break;
				case 'r':
					cvLexeme += '\r';
					advance();
					cvState = LexState.lxstQuoteTxt;
					break;
				case '\uffff':
					cvLexeme += '\\';
					cvUnterminatedString = true;
					cvToken = LexToken.tokenString;
					cvState = LexState.lxstEND;
					break;
				default:
					cvLexeme += cvCharPipe[0];
					advance();
					cvState = LexState.lxstQuoteTxt;
					break;
				}
				break;

			case lxstQuoteEnd:
				if (cvCharPipe[0] == cvLexQuoteChar && cvEmbedDoubleQuotes) {
					cvLexeme += cvCharPipe[0];
					advance();
					cvState = LexState.lxstQuoteTxt;
				}
				else {
					cvToken = LexToken.tokenString;
					cvState = LexState.lxstEND;
				}
				break;

			//-----------------------------------------------------
			// Field Separator
			//
			case lxstFieldSeparator:
				cvLexeme += cvCharPipe[0];
				advance();
				cvToken = LexToken.tokenFieldSeparator;
				cvState = LexState.lxstEND;
				break;

			//-----------------------------------------------------
			// Line Separator
			//
			case lxstLineSeparator:
				switch (cvEolState){
				case lxstEolCh:
					switch (cvCharPipe[0]) {
					case '\r':
						cvLexeme += "\\r";
						advance();
						cvEolState = LexState.lxstEolCR;
						cvState = LexState.lxstLineSeparator;
						break;
					case '\n':
						cvLexeme += "\\n";
						advance();
						cvEolState = LexState.lxstEolLF;
						cvState = LexState.lxstLineSeparator;
						break;
					}
					break;

				case lxstEolCR:
					if (cvCharPipe[0] == '\n') {
						cvLexeme += "\\n";
						advance();
					}
					cvToken = LexToken.tokenLineSeperator;
					cvState = LexState.lxstEND;
					break;

				case lxstEolLF:
					if (cvCharPipe[0] == '\r') {
						cvLexeme += "\\r";
						advance();
					}
					cvToken = LexToken.tokenLineSeperator;
					cvState = LexState.lxstEND;
					break;

				}
				break;

			//-----------------------------------------------------
			// End of Input
			//
			case lxstEndOfIntput:
				cvToken = LexToken.tokenEnd;
				cvState = LexState.lxstEND;
				break;

			default:
				cvState = LexState.lxstBEG;
				break;
			}


		} while (cvState != LexState.lxstEND);
	
		return cvToken;
	}

	//========================================================================================================
}
