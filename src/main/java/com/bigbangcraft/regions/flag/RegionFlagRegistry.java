package com.bigbangcraft.regions.flag;

import java.util.*;

public final class RegionFlagRegistry {
    public static final String CATEGORY_ACCESS = "Acesso";
    public static final String CATEGORY_BUILD = "Construcao";
    public static final String CATEGORY_CONTAINERS = "Containers e Maquinas";
    public static final String CATEGORY_COMBAT = "Combate e Entidades";
    public static final String CATEGORY_WORLD = "Mundo e Ambiente";
    public static final String CATEGORY_MOVEMENT = "Movimentacao e Teleporte";
    public static final String CATEGORY_AUTOMATION = "Automacao";
    public static final String CATEGORY_ADVANCED = "Avancado";

    private static final Map<String, RegionFlagDefinition> FLAGS = new LinkedHashMap<>();

    static {
        register("enter", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite entrar na regiao", "OWNER,LEADER,MANAGER");
        register("visitor-build", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem construir", "OWNER,LEADER");
        register("visitor-interact", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem interagir", "OWNER,LEADER,MANAGER");
        register("visitor-containers", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem acessar containers", "OWNER,LEADER,MANAGER");
        register("visitor-doors", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem usar portas", "OWNER,LEADER,MANAGER");
        register("visitor-buttons", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem usar botoes", "OWNER,LEADER,MANAGER");
        register("visitor-levers", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem usar alavancas", "OWNER,LEADER,MANAGER");
        register("visitor-redstone", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem usar redstone", "OWNER,LEADER,MANAGER");
        register("visitor-item-frames", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem mexer em molduras", "OWNER,LEADER,MANAGER");
        register("visitor-armor-stands", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem mexer em armaduras", "OWNER,LEADER,MANAGER");
        register("visitor-pickup-items", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "ALLOW", "Visitantes podem pegar itens", "OWNER,LEADER,MANAGER");
        register("visitor-drop-items", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "ALLOW", "Visitantes podem dropar itens", "OWNER,LEADER,MANAGER");
        register("visitor-flight", CATEGORY_ACCESS, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem voar", "OWNER,LEADER,MANAGER");

        register("pvp", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "DENY", "Permite PVP", "OWNER,LEADER");
        register("visitor-damage-passives", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem ferir passivos", "OWNER,LEADER");
        register("visitor-damage-hostiles", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem ferir hostis", "OWNER,LEADER");
        register("visitor-projectiles", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "DENY", "Visitantes podem usar projeteis", "OWNER,LEADER");
        register("mob-griefing", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "DENY", "Mobs podem alterar blocos", "OWNER,LEADER");
        register("spawn-monsters", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "ALLOW", "Monstros podem spawnar", "OWNER,LEADER");
        register("spawn-animals", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "ALLOW", "Animais podem spawnar", "OWNER,LEADER");
        register("spawn-spawners", CATEGORY_COMBAT, RegionFlagValueType.BOOLEAN, "ALLOW", "Spawners podem spawnar", "OWNER,LEADER");

        register("water-flow", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Fluxo de agua", "OWNER,LEADER");
        register("lava-flow", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Fluxo de lava", "OWNER,LEADER");
        register("fire-spread", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Propagacao de fogo", "OWNER,LEADER");
        register("fire-block-damage", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Fogo danifica blocos", "OWNER,LEADER");
        register("explosion-block-damage", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Explosoes danificam blocos", "OWNER,LEADER");
        register("piston-movement", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "DENY", "Pistoes movem blocos", "OWNER,LEADER");
        register("leaf-decay", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "ALLOW", "Folhas decaem", "OWNER,LEADER");
        register("crop-growth", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "ALLOW", "Cultivos crescem", "OWNER,LEADER");
        register("block-physics", CATEGORY_WORLD, RegionFlagValueType.BOOLEAN, "ALLOW", "Fisica de blocos", "OWNER,LEADER");

        register("allow-flight-inside", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite voo dentro", "OWNER,LEADER,MANAGER");
        register("allow-ender-pearl", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite ender pearl", "OWNER,LEADER");
        register("allow-chorus-fruit", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite chorus fruit", "OWNER,LEADER");
        register("allow-portals", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite portais", "OWNER,LEADER");
        register("allow-teleport-inside", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite teleporte interno", "OWNER,LEADER");
        register("allow-home-to-region", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite /casa", "OWNER,LEADER");
        register("allow-vehicles", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite veiculos", "OWNER,LEADER");
        register("allow-elytra", CATEGORY_MOVEMENT, RegionFlagValueType.BOOLEAN, "ALLOW", "Permite elytra", "OWNER,LEADER");

        register("allow-fake-player-actions", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite fake players", "OWNER,LEADER");
        register("allow-block-breakers", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite block breakers", "OWNER,LEADER");
        register("allow-block-placers", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite block placers", "OWNER,LEADER");
        register("allow-builders", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite builders", "OWNER,LEADER");
        register("allow-quarries", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite quarries", "OWNER,LEADER");
        register("allow-rftools-builder", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite RFTools Builder", "OWNER,LEADER");
        register("allow-fluid-machines", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite maquinas de fluidos", "OWNER,LEADER");
        register("allow-automation-cross-border", CATEGORY_AUTOMATION, RegionFlagValueType.BOOLEAN, "DENY", "Permite automacao cruzar bordas", "OWNER,LEADER");

        register("priority-mode", CATEGORY_ADVANCED, RegionFlagValueType.ENUM, "DEFAULT", "Modo de prioridade", "OWNER");
        register("max-members", CATEGORY_ADVANCED, RegionFlagValueType.INTEGER, "0", "Limite maximo de membros", "OWNER");
        register("custom-message", CATEGORY_ADVANCED, RegionFlagValueType.STRING_LIST, "", "Mensagem customizada", "OWNER");
        register("allowed-items", CATEGORY_ADVANCED, RegionFlagValueType.ITEM_LIST, "", "Lista de itens permitidos", "OWNER");
        register("allowed-entities", CATEGORY_ADVANCED, RegionFlagValueType.ENTITY_LIST, "", "Lista de entidades permitidas", "OWNER");
    }

    private RegionFlagRegistry() {
    }

    private static void register(String id, String category, RegionFlagValueType valueType, String defaultValue,
                                 String description, String editableBy) {
        FLAGS.put(id, new RegionFlagDefinition(id, category, valueType, defaultValue, description, editableBy));
    }

    public static Optional<RegionFlagDefinition> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(FLAGS.get(id.toLowerCase(Locale.ROOT)));
    }

    public static Collection<RegionFlagDefinition> getAll() {
        return Collections.unmodifiableCollection(FLAGS.values());
    }

    public static List<RegionFlagDefinition> getByCategory(String category) {
        List<RegionFlagDefinition> list = new ArrayList<>();
        for (RegionFlagDefinition def : FLAGS.values()) {
            if (def.getCategory().equalsIgnoreCase(category)) {
                list.add(def);
            }
        }
        return list;
    }

    public static List<String> getCategories() {
        return List.of(CATEGORY_ACCESS, CATEGORY_BUILD, CATEGORY_CONTAINERS, CATEGORY_COMBAT, CATEGORY_WORLD, CATEGORY_MOVEMENT, CATEGORY_AUTOMATION, CATEGORY_ADVANCED);
    }
}
