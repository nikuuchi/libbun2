package org.libbun;

public final class PegRuleSet {
	UMap<Peg>           pegMap;
	UMap<String>        objectLabelMap = null;
	boolean lrExistence = false;
	public boolean foundError = false;

	public PegRuleSet() {
		this.pegMap = new UMap<Peg>();
		this.pegMap.put("indent", new PegIndent(null));  // default rule
	}

	public final boolean hasRule(String ruleName) {
		return this.pegMap.get(ruleName, null) != null;
	}

	public final Peg getRule(String ruleName) {
		return this.pegMap.get(ruleName, null);
	}
	
	public final void setRule(String ruleName, Peg e) {
		Peg checked = this.checkPegRule(ruleName, e);
		if(checked != null) {
			this.pegMap.put(ruleName, checked);
		}
	}

	private Peg checkPegRule(String name, Peg e) {
		if(e instanceof PegChoice) {
			PegChoice newnode = new PegChoice();
			for(int i = 0; i < e.size(); i++) {
				newnode.add(this.checkPegRule(name, e.get(i)));
			}
			if(newnode.size() == 1) {
				return newnode.get(0);
			}
			return newnode;
		}
		if(e instanceof PegLabel) {  // self reference
			if(name.equals(((PegLabel) e).symbol)) {
				Peg defined = this.pegMap.get(name, null);
				if(defined == null) {
					e.warning("undefined self reference: " + name);
				}
//				System.out.println("name " + name + ", " + ((PegLabel) e).symbol + " " + defined);
				return defined;
			}
		}
		return e;
	}
	
	public final void check() {
		this.objectLabelMap = new UMap<String>();
		this.foundError = false;
		UList<String> list = this.pegMap.keys();
		for(int i = 0; i < list.size(); i++) {
			String ruleName = list.ArrayValues[i];
			Peg e = this.pegMap.get(ruleName, null);
			e.verify(ruleName, this);
			if(Main.VerbosePegMode && !Main.VerboseAstMode) {
				System.out.println(e.toPrintableString(ruleName, "\n  = ", "\n  / ", "\n  ;", true));
			}
		}
		if(this.foundError) {
			Main._Exit(1, "peg error found");
		}
	}
	
	public void addObjectLabel(String objectLabel) {
		this.objectLabelMap.put(objectLabel, objectLabel);
	}

	public final boolean loadPegFile(String fileName) {
		ParserContext p = Main.newParserContext(Main.loadSource(fileName));
		p.setRuleSet(PegRules);
		while(p.hasNode()) {
			p.initMemo();
			PegObject node = p.parseNode("TopLevel");
			if(node.isFailure()) {
				Main._Exit(1, "FAILED: " + node);
				break;
			}
			parse(p, node);
		}
		this.check();
		return this.foundError;
	}
	
	private void parse(ParserContext context, PegObject node) {
		if(node.is("#rule")) {
			String ruleName = node.textAt(0, "");
			Peg e = toPeg(node.get(1));
			this.setRule(ruleName, e);
			//System.out.println("#rule** " + node + "\n@@@@ => " + e);
			return;
		}
		if(node.is("#import")) {
			String ruleName = node.textAt(0, "");
			String fileName = context.source.checkFileName(node.textAt(1, ""));
			this.importRuleFromFile(ruleName, fileName);
			return;
		}
		System.out.println("WHAT? parsed: " + node);		
	}
	private Peg toPeg(PegObject node) {
		Peg e = this.toPegImpl(node);
		e.setSource(node.source, node.startIndex);
		return e;
	}	
	private Peg toPegImpl(PegObject node) {
		if(node.is("#name")) {
			return new PegLabel(node.getText());
		}
		if(node.is("#string")) {
			return new PegString(UCharset._UnquoteString(node.getText()));
		}
		if(node.is("#character")) {
			return new PegCharacter(node.getText());
		}
		if(node.is("#any")) {
			return new PegAny();
		}
		if(node.is("#choice")) {
			PegChoice l = new PegChoice();
			for(int i = 0; i < node.size(); i++) {
				l.list.add(toPeg(node.get(i)));
			}
			return l;
		}
		if(node.is("#sequence")) {
			PegSequence l = new PegSequence();
			for(int i = 0; i < node.size(); i++) {
				l.list.add(toPeg(node.get(i)));
			}
			return l;
		}
		if(node.is("#not")) {
			return new PegNot(toPeg(node.get(0)));
		}
		if(node.is("#and")) {
			return new PegAnd(toPeg(node.get(0)));
		}
		if(node.is("#one")) {
			return new PegOneMore(null, toPeg(node.get(0)));
		}
		if(node.is("#zero")) {
			return new PegZeroMore(toPeg(node.get(0)));
		}
		if(node.is("#option")) {
			return new PegOptional(toPeg(node.get(0)));
		}
		if(node.is("#label")) {
			return new PegTag(null, node.getText());
		}
		if(node.is("#newjoin")) {
			Peg seq = toPeg(node.get(0));
			PegNewObject o = new PegNewObject(null, true);
			if(seq.size() > 0) {
				for(int i = 0; i < seq.size(); i++) {
					o.list.add(seq.get(i));
				}
			}
			else {
				o.list.add(seq);
			}
			return o;
		}
		if(node.is("#new")) {
			Peg seq = toPeg(node.get(0));
			PegNewObject o = new PegNewObject(null, false);
			if(seq.size() > 0) {
				for(int i = 0; i < seq.size(); i++) {
					o.list.add(seq.get(i));
				}
			}
			else {
				o.list.add(seq);
			}
			return o;
		}
		if(node.is("#setter")) {
			int index = -1;
			String indexString = node.getText();
			if(indexString.length() > 0) {
				index = (int)UCharset._ParseInt(indexString);
			}
			return new PegSetter(null, toPeg(node.get(0)), index);
		}
		if(node.is("#catch")) {
			return new PegCatch(null, toPeg(node.get(0)));
		}
		Main._Exit(1, "undefined peg: " + node);
		return null;
	}
//
//	public final boolean loadPegFile2(String fileName) {
//		PegSource source = Main.loadSource(fileName);
//		PegParserParser p = new PegParserParser(this, source);
//		while(p.hasRule()) {
//			p.parseRule();
//		}
//		this.check();
//		return true;
//	}

	void importRuleFromFile(String label, String fileName) {
		if(Main.VerbosePegMode) {
			System.out.println("importing " + fileName);
		}
		PegRuleSet p = new PegRuleSet();
		p.loadPegFile(fileName);
		UList<String> list = p.makeList(label);
		String prefix = "";
		int loc = label.indexOf(":");
		if(loc > 0) {
			prefix = label.substring(0, loc+1);
			label = label.substring(loc+1);
			this.pegMap.put(label, new PegLabel(prefix+label));
		}
		for(int i = 0; i < list.size(); i++) {
			String l = list.ArrayValues[i];
			Peg e = p.getRule(l);
			this.pegMap.put(prefix + l, e.clone(prefix));
		}
	}

	public final UList<String> makeList(String startPoint) {
		UList<String> list = new UList<String>(new String[100]);
		UMap<String> set = new UMap<String>();
		Peg e = this.getRule(startPoint);
		if(e != null) {
			list.add(startPoint);
			set.put(startPoint, startPoint);
			e.makeList(this, list, set);
		}
		return list;
	}

	public final void show(String startPoint) {
		UList<String> list = makeList(startPoint);
		for(int i = 0; i < list.size(); i++) {
			String name = list.ArrayValues[i];
			Peg e = this.getRule(name);
			String rule = e.toPrintableString(name, "\n  = ", "\n  / ", "\n  ;", true);
			System.out.println(rule);
		}
	}

	// Definiton of Bun's Peg	
	private final static Peg s(String token) {
		return new PegString(token);
	}
	private final static Peg c(String charSet) {
		return new PegCharacter(charSet);
	}
	public static Peg n(String ruleName) {
		return new PegLabel(ruleName);
	}
	private final static Peg opt(Peg e) {
		return new PegOptional(e);
	}
	private final static Peg zero(Peg e) {
		return new PegZeroMore(e);
	}
	private final static Peg zero(Peg ... elist) {
		return new PegZeroMore(seq(elist));
	}
	private final static Peg one(Peg e) {
		return new PegOneMore(null, e);
	}
	private final static Peg one(Peg ... elist) {
		return new PegOneMore(null, seq(elist));
	}
	private final static Peg seq(Peg ... elist) {
		PegSequence l = new PegSequence();
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	private final static Peg choice(Peg ... elist) {
		PegChoice l = new PegChoice();
		for(Peg e : elist) {
			l.add(e);
		}
		return l;
	}
	public static Peg not(Peg e) {
		return new PegNot(e);
	}
	public static Peg L(String label) {
		return new PegTag(null, label);
	}
	public static Peg O(Peg ... elist) {
		PegNewObject l = new PegNewObject(null, false);
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	public static Peg LO(Peg ... elist) {
		PegNewObject l = new PegNewObject(null, true);
		for(Peg e : elist) {
			l.list.add(e);
		}
		return l;
	}
	public static Peg set(Peg e) {
		return new PegSetter(null, e, -1);
	}

	PegRuleSet loadPegRule() {
		Peg Any = new PegAny();
		Peg NewLine = c("\\r\\n");
//		Comment
//		  = '/*' (!'*/' .)* '*/'
//		  / '//' (![\r\n] .)* [\r\n]
//		  ;
		Peg Comment = choice(
			seq(s("/*"), zero(not(s("*/")), Any), s("*/")),
			seq(s("//"), zero(not(NewLine), Any), NewLine)	
		);
//		_ = 
//		  ([ \t\r\n]+ / Comment )* 
//		  ;
		this.setRule("_", zero(choice(one(c(" \\t\\n\\r")), Comment)));
		
//		RuleName
//		  = << [A-Za-z_] [A-Za-z0-9_]* #name >>
//		  ;
		this.setRule("RuleName", O(c("A-Za-z_"), zero(c("A-Za-z0-9_")), L("#name")));
////	String
////	  = "'" << (!"'" .)*  #string >> "'"
////	  / '"' <<  (!'"' .)* #string >> '"'
////	  ;
		Peg _String = choice(
			seq(s("'"), O(zero(not(s("'")), Any), L("#string")), s("'")),
			seq(s("\""), O(zero(not(s("\"")), Any), L("#string")), s("\""))
		);	
//	Character 
//	  = "[" <<  (!']' .)* #character >> "]"
//	  ;
		Peg _Character = seq(s("["), O(zero(not(s("]")), Any), L("#character")), s("]"));
//	Any
//	  = << '.' #any >>
//	  ;
		Peg _Any = O(s("."), L("#any"));
//	ObjectLabel 
//	  = << '#' [A-z0-9_.]+ #label>>
//	  ;
		Peg _ObjectLabel = O(s("#"), one(c("A-Za-z0-9_.")), L("#label"));
//	Index
//	  = << [0-9] #index >>
//	  ;
		Peg _Index = O(c("0-9"), L("#index"));
//	Setter
//	  = '@' <<@ [0-9]? #setter>>
//	  ;
		setRule("Setter", seq(choice(s("^"), s("@")), LO(opt(c("0-9")), L("#setter"))));
//		SetterTerm
//		  = '(' Expr ')' Setter?
//		  / '<<' << ('@' [ \t\n] #newjoin / '' #new) _? Expr@ >> _? '>>' Setter?
//		  / RuleName Setter?
//		  ;
		Peg _SetterTerm = choice(
			seq(s("("), opt(n("_")), n("Expr"), opt(n("_")), s(")"), opt(n("Setter"))),
			seq(O(choice(s("8<"), s("<<")), choice(seq(choice(s("^"), s("@")), c(" \\t\\n"), L("#newjoin")), seq(s(""), L("#new"))), 
					opt(n("_")), set(n("Expr")), opt(n("_")), choice(s(">8"), s(">>"))), opt(n("Setter"))),
			seq(n("RuleName"), opt(n("Setter")))
		);
//	Term
//	  = String 
//	  / Character
//	  / Any
//	  / ObjectLabel
//	  / Index
//	  / SetterTerm
//	  ;
		setRule("Term", choice(
			_String, _Character, _Any, _ObjectLabel, _Index, _SetterTerm
		));
//
//	SuffixTerm
//	  = Term <<@ ('*' #zero / '+' #one / '?' #option) >>?
//	  ;
		this.setRule("SuffixTerm", seq(n("Term"), opt(LO(choice(seq(s("*"), L("#zero")), seq(s("+"), L("#one")), seq(s("?"), L("#option")))))));
//	Predicated
//	  = << ('&' #and / '!' #not) SuffixTerm@ >> / SuffixTerm 
//	  ;
		this.setRule("Predicate",  choice(
			O(choice(seq(s("&"), L("#and")),seq(s("!"), L("#not"))), set(n("SuffixTerm"))), 
			n("SuffixTerm")
		));
//  Catch
//    = << 'catch' Expr@ >>
//    ;
		Peg Catch = O(s("catch"), n("_"), L("#catch"), set(n("Expr")));
//	Sequence 
//	  = Predicated <<@ (_ Predicated@)+ #seq >>?
//	  ;
		setRule("Sequence", seq(n("Predicate"), opt(LO(L("#sequence"), one(n("_"), set(n("Predicate")))))));
//	Choice
//	  = Sequence <<@ _? ('/' _? Sequence@)+ #choice >>?
//	  ;
		Peg _Choice = seq(n("Sequence"), opt(LO( L("#choice"), one(opt(n("_")), s("/"), opt(n("_")), set(choice(Catch, n("Sequence")))))));
//	Expr
//	  = Choice
//	  ;
		this.setRule("Expr", _Choice);
//	Rule
//	  = << RuleName@ _? '=' _? Expr@ #rule>>
//	  ;
		this.setRule("Rule", O(L("#rule"), set(n("RuleName")), opt(n("_")), s("="), opt(n("_")), set(n("Expr"))));
//	Import
//    = << 'import' _ RuleName@ from String@ #import>>
//		  ;
		this.setRule("Import", O(s("import"), L("#import"), n("_"), set(n("RuleName")), n("_"), s("from"), n("_"), set(_String)));
//	TopLevel   
//	  =  Rule _? ';'
//	  ;
//		this.setRule("TopLevel", seq(n("Rule"), opt(n("_")), s(";"), opt(n("_"))));
		this.setRule("TopLevel", seq(opt(n("_")), choice(n("Rule"), n("Import")), opt(n("_")), s(";")));
		this.check();
		return this;
	}
	
	public final static PegRuleSet PegRules = new PegRuleSet().loadPegRule();


}


