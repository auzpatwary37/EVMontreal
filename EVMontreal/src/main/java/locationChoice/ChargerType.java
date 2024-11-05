package locationChoice;

public enum ChargerType {
	fast{
		@Override
		public String toString() {
			return "Fast";
		}
	},
	level1{
		@Override
		public String toString() {
			return "Level 1";
		}
	},
	level2{
		@Override
		public String toString() {
			return "Level 2";
		}
	},
	home{
		@Override
		public String toString() {
			return "Home";
		}
	}
}
