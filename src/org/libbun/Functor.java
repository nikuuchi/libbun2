package org.libbun;

public class Functor {
	public SymbolTable storedTable = null;
	public String      name;
	public boolean     isSymbol;
	public BunType     funcType;
	TemplateEngine     template;
	public Functor     nextChoice = null;

	public Functor(String name, boolean isSymbol, BunType funcType) {
		this.name = name;
		this.isSymbol = isSymbol;
		this.funcType = funcType;
	}
	public String key() {
		if(this.funcType == null) {
			return this.name + "*";
		}
		else {
			if(this.name.equals("#coercion") || this.name.equals("#conv")) {
				return BunType.keyTypeRel(this.name, this.funcType.get(0), this.funcType.getReturnType());
			}
			if(this.isSymbol) {
				return this.name;
			}
			return this.name + ":" + this.funcType.getFuncParamSize();
		}
	}
	@Override 
	public String toString() {
		return this.key();
	}
	
	public BunType getReturnType(BunType defaultType) {
		if(this.funcType != null) {
			return this.funcType.getReturnType();
		}
		return defaultType;
	}
	protected void matchSubNode(PegObject node, boolean hasNextChoice) {
		SymbolTable gamma = node.getSymbolTable();
		BunType varFuncType = this.getVarFuncType();
		for(int i = 0; i < node.size(); i++) {
			BunType type = this.paramTypeAt(varFuncType, i);
			if(!gamma.checkTypeAt(node, i, type, hasNextChoice)) {
				node.matched = null;
				return;
			}
		}
		this.returnType(varFuncType).typed(gamma, node, true);
		node.matched = this;
//		if(this.funcType instanceof FunctorType) {
//			((FunctorType)this.funcType).check(gamma, node, gamma.root.driver);
//		}
	}
	private BunType getVarFuncType() {
		if(this.funcType != null) {
			//System.out.println("func: " + this.funcType + " greekCount=" + this.funcType.countGreekType());
			return this.funcType.transformGreekTypeToVarType(null);
		}
		return null;
	}
	private BunType paramTypeAt(BunType varFuncType, int index) {
		if(varFuncType != null && index < varFuncType.getFuncParamSize()) {
			return varFuncType.get(index).getRealType();
		}
		return BunType.UntypedType;
	}
	private BunType returnType(BunType varFuncType) {
		if(varFuncType != null) {
			return varFuncType.getReturnType().getRealType();
		}
		return BunType.UntypedType;
	}
	public void build(PegObject node, BunDriver driver) {
		if(this.template != null) {
			this.template.build(node, driver);
		}
	}
	public void add(TemplateEngine section) {
		this.template = section;
	}
	
}


class ErrorFunctor extends Functor {
	public ErrorFunctor() {
		super(BunSymbol.PerrorFunctor, false, null);
	}
	@Override
	protected void matchSubNode(PegObject node, boolean hasNextChoice) {
		node.matched = this;
	}
	@Override
	public void build(PegObject node, BunDriver driver) {
//		PegObject msgNode = node.get(0, null);
//		if(msgNode != null) {
//			String errorMessage = node.getTextAt(0, "*error*");
//			driver.report(node, "error", errorMessage);
//		}
//		else {
		driver.report(node, "error", "syntax error");
//		}
	}
}

class BunTypeDefFunctor extends Functor {
	public BunTypeDefFunctor(String name) {
		super(name, false, null);
	}
	@Override 
	protected void matchSubNode(PegObject node, boolean hasNextChoice) {
		SymbolTable gamma = node.getSymbolTable();
		String name = node.getText();
		gamma.setType(BunType.newValueType(name, null));
		node.matched = this;
	}
	@Override
	public void build(PegObject node, BunDriver driver) {
	}
}

class BunTemplateFunctor extends Functor {
	public BunTemplateFunctor(String name) {
		super(name, false, null);
	}

	@Override 
	protected void matchSubNode(PegObject node, boolean hasNextChoice) {
		SymbolTable gamma = node.getSymbolTable();
		Functor f = this.parseFunctor(gamma, node);
		if(f != null) {
			gamma.addFunctor(f);
		}
		node.matched = this;
	}

	@Override
	public void build(PegObject node, BunDriver driver) {
		// TODO Auto-generated method stub
	}
	
	private Functor parseFunctor(SymbolTable gamma, PegObject bunNode) {
		boolean isSymbol = false;
		String name = bunNode.textAt(0, null);
		GreekList greekList = this.parseGreekList(gamma, bunNode.get(5, null));
		UniMap<Integer> nameMap = new UniMap<Integer>();
		if(bunNode.get(1).isEmptyToken()) {
			isSymbol = true;
		}
		String checkerName = bunNode.textAt(4, null);
		BunType funcType = this.parseFuncType(gamma, greekList, bunNode.get(1), bunNode.get(2), checkerName, nameMap);
		Functor functor = new Functor(name, isSymbol, funcType);
		TemplateEngine section = this.parseSection(bunNode.get(3), nameMap);
		functor.add(section);
		return functor;
	}

	private GreekList parseGreekList(SymbolTable gamma, PegObject paramNode) {
		if(paramNode != null) {
			GreekList listed = null;
			for(int i = 0; i < paramNode.size(); i++) {
				PegObject p = paramNode.get(i);
				String name = p.textAt(0, null);
				listed = this.appendGreekList(listed, name, this.parseType(gamma, null, p.get(1, null)));
			}
			return listed;
		}
		return null;
	}
		
	private GreekList appendGreekList(GreekList listed, String name, BunType parsedType) {
		GreekList entry = new GreekList(name, parsedType);
		if(listed == null) {
			return entry;
		}
		listed.append(entry);
		return listed;
	}

	private BunType parseFuncType(SymbolTable gamma, GreekList greekList, PegObject paramNode, PegObject returnTypeNode, String checkerName, UniMap<Integer> nameMap) {
		if(paramNode.size() == 0 && paramNode.getText().equals("(*)")) {
			return null; // 
		}
		UniArray<BunType> typeList = new UniArray<BunType>(new BunType[paramNode.size()+1]);
		for(int i = 0; i < paramNode.size(); i++) {
			PegObject p = paramNode.get(i);
			String name = p.textAt(0, null);
			typeList.add(this.parseType(gamma, greekList, p.get(1, null)));
			nameMap.put(name, i);
		}
		typeList.add(this.parseType(gamma, greekList, returnTypeNode));
		BunType t = BunType.newFuncType(typeList);
//		if(greekList != null || checkerName != null) {
//			t = new FunctorType(greekList, t, checkerName);
//		}
		return t;
	}

	private BunType parseType(SymbolTable gamma, GreekList greekList, PegObject node) {
		if(node == null) {
			return BunType.UntypedType;
		}
		if(greekList != null) {
			String name = node.getText();
			BunType t = greekList.getGreekType(name);
			if(t != null) {
				return t;
			}
		}
		if(node.is("#type")) {
			if(node.isEmptyToken()) {
				return BunType.newVarType(node);
			}
			String typeName = node.getText();
			BunType t = gamma.getType(typeName, null);
			if(t == null) {
				t = BunType.UntypedType;
			}
			return t;
		}
		if(node.is("#Tvoid")) {
			return BunType.VoidType;
		}
		if(node.is("#Tany")) {
			return BunType.AnyType;
		}
		if(node.is("#Tvar")) {
			return BunType.newVarType(node);
		}
		if(node.is("#Tbun.node")) {
			return BunType.newNodeType(node.getText());
		}
		if(node.is("#Tbun.optional")) {
			return BunType.newOptionalType(node.getText());
		}
		if(node.is("#Tbun.not")) {
			return BunType.newNotType(this.parseType(gamma, greekList, node.get(0, null)));
		}
		if(node.is("#Tarray")) {
			return BunType.newGenericType("Array", this.parseType(gamma, greekList, node.get(0, null)));
		}
		if(node.is("#Tgeneric")) {
			UniArray<BunType> list = new UniArray<BunType>(new BunType[node.size()]);
			for(int i = 1; i < node.size(); i++) {
				list.add(this.parseType(gamma, greekList, node.get(i, null)));
			}
			return BunType.newGenericType(node.textAt(0, ""), list);
		}
		if(node.is("#Tbun.and")) {
			UniArray<BunType> list = new UniArray<BunType>(new BunType[node.size()]);
			for(int i = 0; i < node.size(); i++) {
				list.add(this.parseType(gamma, greekList, node.get(i, null)));
			}
			return BunType.newAndType(list);
		}
		if(node.is("#Tunion")) {
			UniArray<BunType> list = new UniArray<BunType>(new BunType[node.size()]);
			for(int i = 0; i < node.size(); i++) {
				list.add(this.parseType(gamma, greekList, node.get(i, null)));
			}
			return BunType.newUnionType(list);
		}
		System.out.println("#FIXME: " + node.name);
		return BunType.UntypedType;
	}

	private TemplateEngine parseSection(PegObject sectionNode, UniMap<Integer> nameMap) {
		TemplateEngine section = new TemplateEngine();
		int line = 0;
		for(int i = 0; i < sectionNode.size(); i++) {
			PegObject subNode = sectionNode.get(i);
			if(subNode.is("#bun.line")) {
				if(line > 0) {
					section.addNewLine();
				}
				this.parseLine(section, subNode, nameMap);
				line = line + 1;
			}
		}
		return section;
	}

	private final static Integer NoneOfName = -2;
	private int indexOfName(String name, UniMap<Integer> nameMap) {
		if(name.equals("this")) {
			return -1;
		}
		return nameMap.get(name, NoneOfName);
	}
	
	private void parseLine(TemplateEngine sec, PegObject lineNode, UniMap<Integer> nameMap) {
		for(int j = 0; j < lineNode.size(); j++) {
			PegObject chunkNode = lineNode.get(j);
			if(chunkNode.is("#bun.chunk")) {
				String s = chunkNode.getText();
				sec.addCodeChunk(s);
			}
			else if(chunkNode.is("#bun.cmd1")) {
				String name = chunkNode.textAt(0, null);
				int index = this.indexOfName(name, nameMap);
				if(index != -2) {
					sec.addNodeChunk(index);
				}
				else {
					SymbolTable gamma = lineNode.getSymbolTable();
					if(!this.checkCommand(gamma, name)) {
						gamma.report(chunkNode, "warning", "undefined command: " + name);
						return;
					}
					else {
						sec.addCommand(name, -1, chunkNode, 1);
					}
				}
			}
			else if(chunkNode.is("#bun.cmd2")){
				SymbolTable gamma = lineNode.getSymbolTable();
				String cmd = chunkNode.textAt(0, null);
				if(!this.checkCommand(gamma, cmd)) {
					gamma.report(chunkNode, "warning", "undefined command: " + cmd);
					return;
				}
				String name = chunkNode.textAt(1, null);
				int index = this.indexOfName(name, nameMap);
				if(index != -2) {
					sec.addCommand(cmd, index, chunkNode, 2);
				}
				else {
					sec.addCommand(cmd, -1, chunkNode, 1);
				}
			}
		}
	}

	private boolean checkCommand(SymbolTable gamma, String name) {
		return gamma.root.driver.hasCommand(name);
	}

}

class TemplateEngine {
	TempalteChunk chunks = null;
	void add(TempalteChunk chunk) {
		if(this.chunks == null) {
			this.chunks = chunk;
		}
		else {
			TempalteChunk cur = this.chunks;
			while(cur.next != null) {
				cur = cur.next;
			}
			cur.next = chunk;
		}
	}
	void addNewLine() {
		this.add(new CommandChunk("newline", -1, null));
	}
	void addCodeChunk(String text) {
		if(text.length() > 0) {
			this.add(new CodeChunk(text));
		}
	}
	void addNodeChunk(int index) {
		this.add(new NodeChunk(null, index));
	}
	void addCommand(String cmd, int index, PegObject aNode, int aStart) {		
		String[] a = null;
		if(aStart < aNode.size()) {
			a = new String[aNode.size()-aStart];
			for(int i = 0; i < a.length; i++) {
				a[i] = aNode.textAt(aStart + i, "");
			}
		}
		this.add(new CommandChunk(cmd, index, a));
	}
	void addCommand(boolean headerOption, String cmd, String name) {
		String[] a = new String[1];
		a[0] = name;
		this.add(new CommandChunk(cmd, -1, a));
	}
	public void build(PegObject node, BunDriver driver) {
		TempalteChunk cur = this.chunks;
		while(cur != null) {
			cur.push(node, driver);
			cur = cur.next;
		}
	}
	private static final String[] EmptyStringArray = new String[0];
	abstract class TempalteChunk {
		TempalteChunk next = null;
		public abstract void push(PegObject node, BunDriver d);
	}
	class CodeChunk extends TempalteChunk {
		String text;
		CodeChunk(String text) {
			this.text = text;
		}
		@Override
		public void push(PegObject node, BunDriver d) {
			d.pushCode(this.text);
		}
	}
	class NodeChunk extends TempalteChunk {
		int index;
		NodeChunk(String name, int index) {
			this.index = index;
		}
		@Override
		public void push(PegObject node, BunDriver d) {
			d.pushNode(node.get(this.index));
		}
	}
	class CommandChunk extends TempalteChunk {
		String name;
		int index;
		String[] arguments;
		CommandChunk(String name, int index, String[] a) {
			this.name = name;
			this.index = index;
			this.arguments = a;
			if(a == null) {
				this.arguments = EmptyStringArray;
			}
		}
		@Override
		public void push(PegObject node, BunDriver d) {
			if(this.index != -1) {
				node = node.get(this.index);
			}
			d.pushCommand(this.name, node, this.arguments);
		}
	}
}


