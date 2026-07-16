package com.pedrodalben.bigbangessentials.jobs.catalog;

import com.pedrodalben.bigbangessentials.jobs.JobActionType;
import com.pedrodalben.bigbangessentials.jobs.compat.IntegrationStatus;
import com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry;
import com.pedrodalben.bigbangessentials.jobs.contracts.ContractPeriodType;
import com.pedrodalben.bigbangessentials.jobs.slot.JobSlotType;

import java.util.*;

public class JobCatalogRegistry {
    private static final JobCatalogRegistry INSTANCE = new JobCatalogRegistry();

    private final Map<String, JobCatalogDefinition> jobs = new LinkedHashMap<>();
    private final Map<JobCategory, List<JobCatalogDefinition>> byCategory = new LinkedHashMap<>();
    private final Map<String, JobCatalogDefinition> byIntegration = new HashMap<>();

    public static JobCatalogRegistry getInstance() {
        return INSTANCE;
    }

    private JobCatalogRegistry() {
        registerCommonJobs();
        registerSpecializations();
        indexByCategory();
    }

    public Map<String, JobCatalogDefinition> getAllJobs() {
        return Collections.unmodifiableMap(jobs);
    }

    public Optional<JobCatalogDefinition> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<JobCatalogDefinition> getJobsByCategory(JobCategory category) {
        return byCategory.getOrDefault(category, Collections.emptyList());
    }

    public List<JobCatalogDefinition> getJobsByIntegration(String integrationId) {
        List<JobCatalogDefinition> result = new ArrayList<>();
        for (JobCatalogDefinition def : jobs.values()) {
            if (integrationId.equalsIgnoreCase(def.requiredIntegration())) {
                result.add(def);
            }
        }
        return result;
    }

    public List<JobCatalogDefinition> getOperationalJobs() {
        return jobs.values().stream()
            .filter(j -> j.availability().isOperational() && j.enabled())
            .toList();
    }

    public List<JobCatalogDefinition> getJobsNeedingConfiguration() {
        return jobs.values().stream()
            .filter(j -> j.availability() == JobAvailability.CONFIGURATION_REQUIRED)
            .toList();
    }

    public void refreshAvailability() {
        for (JobCatalogDefinition def : jobs.values()) {
            if (!def.enabled()) continue;
            if (def.requiredIntegration() != null) {
                IntegrationStatus status = PokemonIntegrationRegistry.getInstance()
                    .getStatus(def.requiredIntegration());
                if (!status.isOperational()) {
                    continue;
                }
            }
        }
    }

    private void registerCommonJobs() {
        jobs.put("miner", JobCatalogDefinition.builder("miner")
            .displayName("Minerador")
            .description("Extraia minérios e pedras preciosas das profundezas.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .iconMaterialIndex(0)
            .colorOrStyle("§6")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .unlockedByDefault(true)
                .skillPointsEvery(2)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(5.0).baseXp(10.0)
                .baseFragments(1)
                .fragmentMilestoneInterval(25)
                .fragmentMilestoneBonus(5)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY, ContractPeriodType.WEEKLY))
                .maxActiveContracts(1)
                .allowedActionTypes(List.of("BREAK_BLOCK"))
                .build())
            .build());

        jobs.put("lumberjack", JobCatalogDefinition.builder("lumberjack")
            .displayName("Lenhador")
            .description("Derrube árvores e colete madeira das florestas.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.BREAK_BLOCK))
            .iconMaterialIndex(1)
            .colorOrStyle("§a")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(4.0).baseXp(8.0)
                .baseFragments(1)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY))
                .allowedActionTypes(List.of("BREAK_BLOCK"))
                .build())
            .build());

        jobs.put("farmer", JobCatalogDefinition.builder("farmer")
            .displayName("Agricultor")
            .description("Cultive e colha plantações férteis.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.HARVEST_CROP))
            .iconMaterialIndex(13)
            .colorOrStyle("§e")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(4.0).baseXp(10.0)
                .baseFragments(1)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY))
                .allowedActionTypes(List.of("HARVEST_CROP"))
                .build())
            .build());

        jobs.put("explorer", JobCatalogDefinition.builder("explorer")
            .displayName("Explorador")
            .description("Descubra biomas e estruturas escondidas pelo mundo.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.EXPLORE))
            .iconMaterialIndex(2)
            .colorOrStyle("§b")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_PRIMARY)
                .unlockedByDefault(true)
                .requiredRankOrder(1)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(15.0).baseXp(25.0)
                .baseFragments(2)
                .build())
            .build());

        jobs.put("fisher", JobCatalogDefinition.builder("fisher")
            .displayName("Pescador")
            .description("Pesque peixes e tesouros aquáticos.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.FISH))
            .iconMaterialIndex(3)
            .colorOrStyle("§9")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(6.0).baseXp(12.0)
                .baseFragments(1)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY))
                .allowedActionTypes(List.of("FISH"))
                .build())
            .build());

        jobs.put("artisan", JobCatalogDefinition.builder("artisan")
            .displayName("Artesão")
            .description("Produza itens através de crafting manual.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.CRAFT_ITEM))
            .iconMaterialIndex(4)
            .colorOrStyle("§d")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(3.0).baseXp(6.0)
                .baseFragments(1)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY, ContractPeriodType.WEEKLY))
                .allowedActionTypes(List.of("CRAFT_ITEM"))
                .build())
            .build());

        jobs.put("blacksmith", JobCatalogDefinition.builder("blacksmith")
            .displayName("Ferreiro")
            .description("Fundir minérios e criar ligas metálicas.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.SMELT_ITEM))
            .iconMaterialIndex(5)
            .colorOrStyle("§8")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(true)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(3.0).baseXp(7.0)
                .baseFragments(1)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY))
                .allowedActionTypes(List.of("SMELT_ITEM"))
                .build())
            .build());

        jobs.put("poke_chef", JobCatalogDefinition.builder("poke_chef")
            .displayName("PokéChef")
            .description("Prepare receitas culinárias Pokémon e comuns.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.CRAFT_ITEM, JobActionType.SMELT_ITEM))
            .iconMaterialIndex(6)
            .colorOrStyle("§6")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(true)
                .requiredRankOrder(1)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(5.0).baseXp(10.0)
                .baseFragments(2)
                .build())
            .build());

        jobs.put("builder", JobCatalogDefinition.builder("builder")
            .displayName("Construtor")
            .description("Construa estruturas dentro de projetos validados.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .availability(JobAvailability.INTEGRATION_MISSING)
            .unavailabilityReason("Sistema de projetos/regiões não disponível.")
            .acceptedActions(List.of(JobActionType.PLACE_BLOCK))
            .requiredIntegration("bigbangregions")
            .iconMaterialIndex(7)
            .colorOrStyle("§7")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(false)
                .requiredRankOrder(3)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(10.0).baseXp(20.0)
                .baseFragments(3)
                .build())
            .build());

        jobs.put("merchant", JobCatalogDefinition.builder("merchant")
            .displayName("Comerciante")
            .description("Entregue contratos e mercadorias por recompensa.")
            .category(JobCategory.COMMON)
            .enabled(true)
            .acceptedActions(List.of(JobActionType.CONTRACT_DELIVERED))
            .iconMaterialIndex(8)
            .colorOrStyle("§e")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.COMMON_SECONDARY)
                .unlockedByDefault(true)
                .requiredRankOrder(2)
                .build())
            .rewardProfile(JobRewardProfile.builder()
                .baseCoins(25.0).baseXp(50.0)
                .baseFragments(5)
                .build())
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(true)
                .availablePeriods(List.of(ContractPeriodType.DAILY, ContractPeriodType.WEEKLY))
                .maxActiveContracts(2)
                .allowedActionTypes(List.of("CONTRACT_DELIVERED"))
                .contractWeight(1.5)
                .build())
            .build());
    }

    private void registerSpecializations() {
        PokemonIntegrationRegistry integRegistry = PokemonIntegrationRegistry.getInstance();

        registerPokemonSpecialization("pokemon_researcher", "Pesquisador Pokémon",
            "Capture Pokémon e registre novas entradas na Pokédex.",
            List.of(JobActionType.POKEMON_CAPTURED, JobActionType.DEX_ENTRY_ADDED),
            "cobblemon", 1, 9,
            JobRewardProfile.builder().baseCoins(20.0).baseXp(40.0).baseFragments(3)
                .fragmentMilestoneInterval(10).fragmentMilestoneBonus(10).build(),
            integRegistry);

        registerPokemonSpecialization("paleontologist", "Paleontólogo",
            "Reviva fósseis e traga Pokémon ancestrais de volta à vida.",
            List.of(JobActionType.FOSSIL_REVIVED),
            "fossils", 3, 10,
            JobRewardProfile.builder().baseCoins(50.0).baseXp(100.0).baseFragments(5).build(),
            integRegistry);

        registerPokemonSpecialization("pokemon_breeder", "Criador Pokémon",
            "Crie ovos e acompanhe a eclosão de novos Pokémon.",
            List.of(JobActionType.EGG_CREATED, JobActionType.EGG_HATCHED),
            "breeding", 2, 11,
            JobRewardProfile.builder().baseCoins(30.0).baseXp(60.0).baseFragments(4).build(),
            integRegistry);

        registerPokemonSpecialization("pasture_keeper", "Cuidador de Pasture",
            "Gerencie o pasture e complete tarefas de manejo Pokémon.",
            List.of(JobActionType.PASTURE_TASK_COMPLETED),
            "pasture", 2, 12,
            JobRewardProfile.builder().baseCoins(15.0).baseXp(30.0).baseFragments(2).build(),
            integRegistry);

        registerPokemonSpecialization("league_trainer", "Treinador da Liga",
            "Vença batalhas contra treinadores e líderes de ginásio.",
            List.of(JobActionType.TRAINER_BATTLE_WON),
            "trainers", 3, 13,
            JobRewardProfile.builder().baseCoins(40.0).baseXp(80.0).baseFragments(5).build(),
            integRegistry);

        registerPokemonSpecialization("raid_specialist", "Especialista em Raids",
            "Participe e conclua raids de forma decisiva.",
            List.of(JobActionType.RAID_CLEARED),
            "raid_dens", 4, 14,
            JobRewardProfile.builder().baseCoins(60.0).baseXp(120.0).baseFragments(8).build(),
            integRegistry);

        registerPokemonSpecialization("pokemon_architect", "Arquiteto Pokémon",
            "Construa projetos Pokémon de grande escala.",
            List.of(JobActionType.PLACE_BLOCK),
            "bigbangregions", 5, 7,
            JobRewardProfile.builder().baseCoins(80.0).baseXp(150.0).baseFragments(10).build(),
            integRegistry);
    }

    private void registerPokemonSpecialization(String jobId, String displayName, String description,
                                                List<JobActionType> actions, String integration,
                                                int requiredRank, int iconIndex,
                                                JobRewardProfile rewardProfile,
                                                PokemonIntegrationRegistry integRegistry) {
        IntegrationStatus status = integRegistry.getStatus(integration);
        boolean integAvailable = status.isOperational();

        jobs.put(jobId, JobCatalogDefinition.builder(jobId)
            .displayName(displayName)
            .description(description)
            .category(JobCategory.POKEMON_SPECIALIZATION)
            .enabled(true)
            .availability(integAvailable ? JobAvailability.AVAILABLE : JobAvailability.INTEGRATION_MISSING)
            .unavailabilityReason(integAvailable ? null
                : "Integração " + integration + " não disponível: " + status.details())
            .acceptedActions(actions)
            .requiredIntegration(integration)
            .iconMaterialIndex(iconIndex)
            .colorOrStyle("§5")
            .requirements(JobRequirements.builder()
                .slotType(JobSlotType.POKEMON_SPECIALIZATION)
                .unlockedByDefault(false)
                .requiredRankOrder(requiredRank)
                .licenseRequired(true)
                .requiredIntegration(integration)
                .build())
            .rewardProfile(rewardProfile)
            .contractProfile(JobContractProfile.builder()
                .contractsEnabled(false)
                .build())
            .build());
    }

    private void indexByCategory() {
        for (JobCategory cat : JobCategory.values()) {
            byCategory.put(cat, new ArrayList<>());
        }
        for (JobCatalogDefinition def : jobs.values()) {
            byCategory.computeIfAbsent(def.category(), k -> new ArrayList<>()).add(def);
            if (def.requiredIntegration() != null) {
                byIntegration.put(def.requiredIntegration(), def);
            }
        }
    }
}
