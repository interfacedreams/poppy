package dev.tommy.daylightpilot;

/** API capabilities shared by Settings and request construction. */
final class ModelOptions {
    enum Model {
        LUNA("Luna", "gpt-5.6-luna"), TERRA("Terra", "gpt-5.6-terra"),
        SOL("Sol", "gpt-5.6-sol"), ASTRA("Astra", "gpt-6-astra");
        final String label, id;
        Model(String label, String id) { this.label = label; this.id = id; }
        @Override public String toString() { return label; }
        Effort[] efforts() {
            return this == ASTRA
                ? new Effort[]{Effort.LOW, Effort.MEDIUM, Effort.HIGH, Effort.XHIGH, Effort.MAX}
                : Effort.values();
        }
        Effort normalize(Effort effort) {
            return this == ASTRA && effort == Effort.NONE ? Effort.LOW : effort;
        }
        static Model fromId(String id) {
            for (Model model : values()) if (model.id.equals(id)) return model;
            return LUNA;
        }
    }
    enum Effort {
        NONE("None", "none"), LOW("Low", "low"), MEDIUM("Medium", "medium"),
        HIGH("High", "high"), XHIGH("Extra high", "xhigh"), MAX("Max", "max");
        final String label, id;
        Effort(String label, String id) { this.label = label; this.id = id; }
        @Override public String toString() { return label; }
        static Effort fromId(String id) {
            for (Effort effort : values()) if (effort.id.equals(id)) return effort;
            return LOW;
        }
    }
    final Model model;
    final Effort effort;
    ModelOptions(String modelId, String effortId) {
        model = Model.fromId(modelId);
        effort = model.normalize(Effort.fromId(effortId));
    }
    int outputTokenLimit() { return effort == Effort.NONE ? 1000 : 25000; }
}
