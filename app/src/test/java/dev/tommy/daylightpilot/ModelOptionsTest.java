package dev.tommy.daylightpilot;

/** Standalone checks for persisted settings and cross-model compatibility. */
public final class ModelOptionsTest {
    public static void main(String[] args) {
        ModelOptions defaults = new ModelOptions(null, null);
        assert defaults.model == ModelOptions.Model.LUNA;
        assert defaults.effort == ModelOptions.Effort.LOW;
        ModelOptions stale = new ModelOptions("retired-model", "unknown-effort");
        assert stale.model == defaults.model && stale.effort == defaults.effort;
        for (ModelOptions.Model model : ModelOptions.Model.values()) {
            for (ModelOptions.Effort effort : ModelOptions.Effort.values()) {
                ModelOptions restored = new ModelOptions(model.id, effort.id);
                assert restored.model == model;
                boolean offered = false;
                for (ModelOptions.Effort option : model.efforts()) offered |= option == restored.effort;
                assert offered : "A restored setting must be selectable";
                if (model == ModelOptions.Model.ASTRA && effort == ModelOptions.Effort.NONE)
                    assert restored.effort == ModelOptions.Effort.LOW;
                else assert restored.effort == effort : "Keep supported thinking levels across models";
                assert restored.outputTokenLimit() == (restored.effort == ModelOptions.Effort.NONE ? 1000 : 25000);
            }
        }
        System.out.println("Model options regression checks passed");
    }
}
