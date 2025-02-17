package modtools.ui.components.input.highlight;

import arc.graphics.Color;
import arc.struct.*;
import mindustry.graphics.Pal;
import modtools.ui.components.input.area.TextAreaTable;
import modtools.ui.components.input.area.TextAreaTable.MyTextArea;

public class Syntax {

	public static final Color
			stringC     = new Color(0xce9178FF),
			keywordC    = new Color(0x569cd6FF),
			numberC     = new Color(0xb5cea8FF),
			commentC    = new Color(0x6a9955FF),
			bracketsC   = new Color(0xffd704FF),
			operatCharC = Pal.accentBack,
			functionsC  = Color.sky,//Color.valueOf("#ae81ff")
			objectsC    = new Color(0x66d9efFF);
	/*public static class Node {
		public boolean has;
		public Node parent;
		public Node left;
		public Node right;

		public Node(boolean has, Node parent, Node left, Node right) {
			this.has = has;
			this.parent = parent;
			this.left = left;
			this.right = right;
		}

		static Node currentNode = null;

		static Node node(boolean has, Node parent, Node left, Node right) {
			Node node = new Node(has, parent, left, right);
			currentNode = node;
			return node;
		}

		static Node node(boolean has, Node left, Node right) {
			return node(has, currentNode, left, right);
		}

	}*/
	// public static JsonReader reader = new JsonReader();

	public final TextAreaTable areaTable;

	public Syntax(TextAreaTable table) {
		areaTable = table;
		area = areaTable.getArea();
	}

	/** 判断指定index，是否为单词边界 */
	public boolean isWordBreak(int i) {
		return isWordBreak(displayText.charAt(i));
	}

	public boolean isWordBreak(char c) {
		return !((48 <= c && c <= 57) || (65 <= c && c <= 90)
		         || (97 <= c && c <= 122) || (19968 <= c && c <= 40869)
		         || c == '$' || c == '_');
	}

	public boolean isWhitespace(char ch) {
		return !Character.isWhitespace(ch);
	}

	public void drawDefText(int start, int max) {
		area.font.setColor(defalutColor);
		area.drawMultiText(displayText, start, max);
	}

	void reset() {
		if (cTask != null) {
			cTask.reset();
			lastTask = cTask;
		}
		cTask = null;
	}

	public void highlightingDraw(String displayText) {
		this.displayText = displayText;
		cTask = null;
		reset();
		// String result;
		for (DrawTask drawTask : taskArr) {
			drawTask.init();
		}
		int lastIndex = 0;
		len = displayText.length();
		lastChar = '\n';
		out:
		for (int i = 0; i < len; i++, lastChar = c) {
			c = displayText.charAt(i);

			if (cTask == null) {
				for (DrawTask drawTask : taskArr) {
					if (!drawTask.draw(i)) {
						drawTask.reset();
						continue;
					}
					cTask = drawTask;
					if (cTask.isFinished()) {
						lastIndex = drawAndReset(i);
						continue out;
					}
					break;
				}
			} else l1:if (cTask.draw(i)) {
				if (!cTask.isFinished()) break l1;
				lastIndex = drawAndReset(i);
			} else {
				reset();
			}
			if (cTask == null) {
				if (lastIndex >= i + 1) continue;

				drawDefText(lastIndex, i + 1);
				lastIndex = i + 1;
			}
		}
		if (cTask != null && cTask.crazy) {
			cTask.drawText(len - 1);
			reset();
		} else if (lastIndex < len) {
			drawDefText(lastIndex, len);
		}
	}
	private int drawAndReset(int i) {
		cTask.drawText(i);
		reset();
		return i + 1;
	}


	public MyTextArea area;
	public String     displayText;

	public Color defalutColor = Color.white;
	char c, lastChar;
	int len;

	/**
	 * 当前任务
	 */
	public DrawTask cTask, lastTask; // default for null.
	/**
	 * 所有的任务
	 */
	public DrawTask[] taskArr = {};

	class DrawToken extends DrawTask {
		// IntMap<?>[] total;
		// IntMap<?>[] current;
		boolean begin = false, finished;
		TokenDraw[] tokenDraws;
		int         lastTokenIndex = -1;
		String      lastToken, token;

		public DrawToken(TokenDraw... tokenDraws) {
			super(new Color());
			this.tokenDraws = tokenDraws;
		}

		void reset() {
			super.reset();
			// System.arraycopy(total, 0, current, 0, total.length);
			lastTokenIndex = -1;
			finished = false;
			begin = false;
		}

		void init() {
			super.init();
			lastToken = null;
			token = null;
		}

		boolean isFinished() {
			return finished;
		}


		void setColor(String token) {
			this.token = token;
			color.set(defalutColor);
			// Log.info(token);
			Color newColor;
			for (TokenDraw draw : tokenDraws) {
				/* 只要draw结果为null，就下一个 */
				if ((newColor = draw.draw(this)) == null) continue;

				color.set(newColor);
				break;
			}
			finished = true;
			lastTokenIndex = lastIndex;
			lastToken = token;
		}

		boolean draw(int i) {
			// if (!current.containsKey(c)) return false;
			if (!(begin || (isWordBreak(lastChar) && !isWordBreak(c)))) return false;
			if (!begin) begin = true;
			if (lastIndex == -1) lastIndex = i;

			/* 判断下一个index是否越界 */
			if (++i < len) {
				/* 判断下一个index是否越界 */
				if (isWordBreak(i)) {
					setColor(displayText.substring(lastIndex, i));
					return finished;
				}
				return true;
			} else {
				setColor(displayText.substring(lastIndex));
				return finished;
			}
		}
	}

	class DrawSymbol extends DrawTask {
		final IntSet symbols;
		Character lastSymbol;

		public DrawSymbol(IntSet map, Color color) {
			super(color);
			symbols = map;
		}

		boolean isFinished() {
			return true;
		}
		void init() {
			super.init();
			lastSymbol = null;
		}

		boolean draw(int i) {
			if (symbols.contains(c)) {
				lastSymbol = c;
				lastIndex = i;
				return true;
			}
			return false;
		}
	}

	class DrawComment extends DrawTask {

		public DrawComment(Color color) {
			super(color, true);
		}

		void reset() {
			super.reset();
			body = false;
			finished = false;
		}

		boolean isFinished() {
			return finished;
		}

		private boolean finished, body, multi;

		@Override
		boolean draw(int i) {
			if (body) {
				if (multi ? lastChar == '*' && c == '/' : c == '\n' || i + 1 >= len) {
					finished = true;
				}
				return true;
			}
			if (c == '/' && i + 1 < len) {
				char next = displayText.charAt(i + 1);
				if (next == '*' || next == '/') {
					lastIndex = i;
					multi = next == '*';
					body = true;
					return true;
				}
			}
			return false;
		}
	}

	public static IntMap<Boolean> chars = IntMap.of(
			'\'', false,
			'"', false,
			'`', true);

	class DrawString extends DrawTask {
		public DrawString(Color color) {
			this(color, chars);
		}
		public DrawString(Color color, IntMap<Boolean> chars) {
			super(color, true);
			map = chars;
		}

		public IntMap<Boolean> map;
		void reset() {
			super.reset();
			leftQuote = rightQuote = false;
		}

		@Override
		boolean isFinished() {
			return rightQuote;
		}

		boolean leftQuote, rightQuote;
		char quote;

		@Override
		boolean draw(int i) {
			if (!leftQuote) {
				if (map.containsKey(c)) {
					quote = c;
					leftQuote = true;
					lastIndex = i;
					return true;
				} else {
					return false;
				}
			}
			if ((quote == c && lastChar != '\\') || (c == '\n' && !map.get(quote))) {
				rightQuote = true;
				leftQuote = false;
				return true;
			}
			return true;
		}
	}


	interface TokenDraw {
		/**
		 * @return Color 如果为null，则不渲染
		 **/
		Color draw(DrawToken task);
	}

	public abstract class DrawTask {
		final Color color;
		boolean crazy;
		int     lastIndex;

		public DrawTask(Color color, boolean crazy) {
			this.color = color;
			this.crazy = crazy;
		}

		public DrawTask(Color color) {
			this(color, false);
		}

		/**
		 * 循环开始时，执行
		 */
		void init() {
		}

		/**
		 * 渲染结束（包括失败）时，执行
		 */
		void reset() {
			lastIndex = -1;
		}

		abstract boolean isFinished();

		abstract boolean draw(int i);

		public void drawText(int i) {
			if (lastIndex == -1) return;
			area.font.setColor(color);
			area.drawMultiText(displayText, lastIndex, i + 1);
		}

		public boolean nextIs(int i, char c) {
			return i + 1 < len && c == displayText.charAt(i + 1);
		}
	}
}
