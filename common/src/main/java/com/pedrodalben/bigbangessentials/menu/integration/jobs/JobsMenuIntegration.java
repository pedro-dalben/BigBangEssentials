package com.pedrodalben.bigbangessentials.menu.integration.jobs;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.action.*;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.placeholder.*;
import com.pedrodalben.bigbangessentials.menu.integration.jobs.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class JobsMenuIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobsMenuIntegration.class);
    private static JobsMenuIntegration instance;

    public static synchronized JobsMenuIntegration getInstance() {
        if (instance == null) {
            instance = new JobsMenuIntegration();
        }
        return instance;
    }

    public void register(Path configDir) {
        // Write default menus to config directory if they don't exist
        setupDefaultMenus(configDir);

        MenuSystem menuSystem = MenuSystem.getInstance();

        // 1. Register Data Providers
        menuSystem.getDataProviderRegistry().registerProvider("jobs.all", new JobsMenuDataProvider());

        // 2. Register Actions
        menuSystem.getActionRegistry().registerActionHandler("join_job", new JoinJobMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("leave_job", new LeaveJobMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("toggle_job", new ToggleJobMenuAction());
        menuSystem.getActionRegistry().registerActionHandler("open_job_details", new OpenJobDetailsMenuAction());

        // 3. Register Placeholders
        menuSystem.getPlaceholderRegistry().registerPlaceholder("jobs", new JobsPlaceholderResolver());

        LOGGER.info("Jobs menu integration registered successfully.");
    }

    private void setupDefaultMenus(Path dir) {
        try {
            Files.createDirectories(dir);
            String[] menus = new String[]{"jobs_menu.yml", "job_details_menu.yml", "pokemon_jobs_menu.yml", "pokemon_job_details_menu.yml", "job_license_menu.yml", "job_milestones_menu.yml", "specialist_crate_menu.yml"};
            for (String menu : menus) {
                Path dest = dir.resolve(menu);
                if (!Files.exists(dest)) {
                    try (java.io.InputStream in = getClass().getResourceAsStream("/default-config/bigbangessentials/menus/" + menu)) {
                        if (in != null) {
                            Files.copy(in, dest);
                        } else {
                            Files.writeString(dest, getHardcodedDefault(menu));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to copy/setup default jobs menus in directory {}: {}", dir, e.getMessage(), e);
        }
    }

    private String getHardcodedDefault(String filename) {
        if ("jobs_menu.yml".equals(filename)) {
            return getJobsMenuYaml();
        } else if ("job_details_menu.yml".equals(filename)) {
            return getJobDetailsMenuYaml();
        } else if ("pokemon_jobs_menu.yml".equals(filename)) {
            return getPokemonJobsMenuYaml();
        } else if ("pokemon_job_details_menu.yml".equals(filename)) {
            return getPokemonJobDetailsMenuYaml();
        } else if ("job_license_menu.yml".equals(filename)) {
            return getJobLicenseMenuYaml();
        } else if ("job_milestones_menu.yml".equals(filename)) {
            return getJobMilestonesMenuYaml();
        } else if ("specialist_crate_menu.yml".equals(filename)) {
            return getSpecialistCrateMenuYaml();
        }
        return "";
    }

    private String getJobsMenuYaml() {
        return "id: \"jobs_menu\"\n" +
               "size: 54\n" +
               "title: \"<gold>Trabalhos e Profissões\"\n\n" +
               "pagination:\n" +
               "  enabled: true\n" +
               "  source: \"jobs.all\"\n" +
               "  content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]\n\n" +
               "dynamic-item-template:\n" +
               "  item:\n" +
               "    material-id: \"{job_icon}\"\n" +
               "    display-name: \"{job_status_color}{job_display_name}\"\n" +
               "    lore:\n" +
               "      - \"<gray>{job_description}\"\n" +
               "      - \"\"\n" +
               "      - \"<gray>Nível: <white>{job_level}\"\n" +
               "      - \"<gray>XP: <white>{job_xp} / {job_xp_required}\"\n" +
               "      - \"{job_xp_progress_bar}\"\n" +
               "      - \"<gray>Ganhos Hoje: <white>${job_earnings} / ${job_limit}\"\n" +
               "      - \"<gray>Categoria: <white>{job_category_label}\"\n" +
               "      - \"<gray>Licença: {job_license_label}\"\n" +
               "      - \"<gray>Slot Ocupado: <yellow>{job_slot_assigned}\"\n" +
               "      - \"<gray>Status: {job_status_color}{job_status}\"\n" +
               "      - \"\"\n" +
               "      - \"<green>Clique Esquerdo: Ver detalhes / recompensas\"\n" +
               "      - \"<yellow>Clique Direito: Entrar ou Sair da profissão\"\n" +
               "  actions:\n" +
               "    - type: \"open_job_details\"\n" +
               "      params:\n" +
               "        job-id: \"{job_id}\"\n" +
               "      clicks: [\"LEFT\"]\n" +
               "    - type: \"toggle_job\"\n" +
               "      params:\n" +
               "        job-id: \"{job_id}\"\n" +
               "      clicks: [\"RIGHT\"]\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      summary:\n" +
               "        slot: 4\n" +
               "        item:\n" +
               "          material-id: \"minecraft:book\"\n" +
               "          display-name: \"<gold>Resumo de Profissões\"\n" +
               "          lore:\n" +
               "            - \"<gray>Trabalhos Ativos: <white>{jobs:active_count} / {jobs:max_active}\"\n" +
               "            - \"<gray>Limite de Ganhos Hoje: <white>${jobs:total_earnings} / ${jobs:global_limit}\"\n" +
               "            - \"<gray>Bônus VIP: <green>{jobs:vip_bonus}\"\n" +
               "      close_btn:\n" +
               "        slot: 49\n" +
               "        item:\n" +
               "          material-id: \"minecraft:barrier\"\n" +
               "          display-name: \"<red>Fechar\"\n" +
               "        actions:\n" +
               "          - type: \"close_menu\"\n" +
               "      prev_btn:\n" +
               "        slot: 48\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Página Anterior\"\n" +
               "        actions:\n" +
               "          - type: \"previous_page\"\n" +
               "      next_btn:\n" +
               "        slot: 50\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<yellow>Próxima Página\"\n" +
               "        actions:\n" +
               "          - type: \"next_page\"\n";
    }

    private String getJobDetailsMenuYaml() {
        return "id: \"job_details_menu\"\n" +
               "size: 27\n" +
               "title: \"<gold>Detalhes: {context:job_display_name}\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      job_info:\n" +
               "        slot: 11\n" +
               "        item:\n" +
               "          material-id: \"{context:job_icon}\"\n" +
               "          display-name: \"{context:job_status_color}{context:job_display_name}\"\n" +
               "          lore:\n" +
               "            - \"<gray>{context:job_description}\"\n" +
               "            - \"\"\n" +
               "            - \"<gray>Nível Atual: <white>{context:job_level}\"\n" +
               "            - \"<gray>XP: <white>{context:job_xp} / {context:job_xp_required}\"\n" +
               "            - \"{context:job_xp_progress_bar}\"\n" +
               "            - \"<gray>Limite Diário: <white>${context:job_earnings} / ${context:job_limit}\"\n" +
               "            - \"<gray>Categoria: <white>{context:job_category_label}\"\n" +
               "            - \"<gray>Licença: {context:job_license_label}\"\n" +
               "            - \"<gray>Slot Ocupado: <yellow>{context:job_slot_assigned}\"\n" +
               "            - \"<gray>Status: {context:job_status_color}{context:job_status}\"\n" +
               "      toggle_action:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:lever\"\n" +
               "          display-name: \"<yellow>Alterar Estado de Atividade\"\n" +
               "          lore:\n" +
               "            - \"<gray>Clique para entrar ou sair\"\n" +
               "            - \"<gray>deste trabalho.\"\n" +
               "        actions:\n" +
               "          - type: \"toggle_job\"\n" +
               "            params:\n" +
               "              job-id: \"{context:job_id}\"\n" +
               "      back_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar ao Menu Principal\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"jobs_menu\"\n";
    }

    private String getPokemonJobsMenuYaml() {
        return "id: \"pokemon_jobs_menu\"\n" +
               "size: 54\n" +
               "title: \"<aqua>Profissões Pokémon\"\n\n" +
               "pagination:\n" +
               "  enabled: true\n" +
               "  source: \"jobs.all\"\n" +
               "  content-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34]\n\n" +
               "dynamic-item-template:\n" +
               "  item:\n" +
               "    material-id: \"{job_icon}\"\n" +
               "    display-name: \"{job_status_color}{job_display_name}\"\n" +
               "    lore:\n" +
               "      - \"<gray>{job_description}\"\n" +
               "      - \"\"\n" +
               "      - \"<gray>Nível: <white>{job_level}\"\n" +
               "      - \"<gray>XP: <white>{job_xp} / {job_xp_required}\"\n" +
               "      - \"{job_xp_progress_bar}\"\n" +
               "      - \"<gray>Especialização Pokémon: <aqua>Ativa\"\n" +
               "      - \"<gray>Licença: {job_license_label}\"\n" +
               "      - \"<gray>Status: {job_status_color}{job_status}\"\n" +
               "      - \"\"\n" +
               "      - \"<green>Clique Esquerdo: Ver detalhes / marcos\"\n" +
               "      - \"<yellow>Clique Direito: Entrar na Especialização\"\n" +
               "  actions:\n" +
               "    - type: \"open_job_details\"\n" +
               "      params:\n" +
               "        job-id: \"{job_id}\"\n" +
               "      clicks: [\"LEFT\"]\n" +
               "    - type: \"toggle_job\"\n" +
               "      params:\n" +
               "        job-id: \"{job_id}\"\n" +
               "      clicks: [\"RIGHT\"]\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      summary:\n" +
               "        slot: 4\n" +
               "        item:\n" +
               "          material-id: \"minecraft:beacon\"\n" +
               "          display-name: \"<aqua>Resumo de Especializações Pokémon\"\n" +
               "          lore:\n" +
               "            - \"<gray>As profissões Pokémon são integradas ao Cobbleverse!\"\n" +
               "            - \"<gray>Requerem RankUp e missões de licença.\"\n" +
               "      close_btn:\n" +
               "        slot: 49\n" +
               "        item:\n" +
               "          material-id: \"minecraft:barrier\"\n" +
               "          display-name: \"<red>Fechar\"\n" +
               "        actions:\n" +
               "          - type: \"close_menu\"\n" +
               "      back_btn:\n" +
               "        slot: 48\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<yellow>Voltar a Trabalhos\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"jobs_menu\"\n";
    }

    private String getPokemonJobDetailsMenuYaml() {
        return "id: \"pokemon_job_details_menu\"\n" +
               "size: 27\n" +
               "title: \"<aqua>Especialização: {context:job_display_name}\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      job_info:\n" +
               "        slot: 11\n" +
               "        item:\n" +
               "          material-id: \"{context:job_icon}\"\n" +
               "          display-name: \"{context:job_status_color}{context:job_display_name}\"\n" +
               "          lore:\n" +
               "            - \"<gray>{context:job_description}\"\n" +
               "            - \"\"\n" +
               "            - \"<gray>Nível Atual: <white>{context:job_level}\"\n" +
               "            - \"<gray>XP: <white>{context:job_xp} / {context:job_xp_required}\"\n" +
               "            - \"<gray>Chaves de Especialista: <gold>Ativas\"\n" +
               "      milestones_btn:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:nether_star\"\n" +
               "          display-name: \"<gold>Marcos e Progresso\"\n" +
               "          lore:\n" +
               "            - \"<gray>Clique para ver seus marcos de carreira\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"job_milestones_menu\"\n" +
               "      back_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"pokemon_jobs_menu\"\n";
    }

    private String getJobLicenseMenuYaml() {
        return "id: \"job_license_menu\"\n" +
               "size: 27\n" +
               "title: \"<gold>Licença de Profissão\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      license_info:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:paper\"\n" +
               "          display-name: \"<gold>Licença Permanente\"\n" +
               "          lore:\n" +
               "            - \"<gray>Para desbloquear esta profissão/especialização,\"\n" +
               "            - \"<gray>você deve completar a missão de licença.\"\n" +
               "            - \"\"\n" +
               "            - \"<green>Clique para iniciar ou resgatar licença\"\n" +
               "      back_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"jobs_menu\"\n";
    }

    private String getJobMilestonesMenuYaml() {
        return "id: \"job_milestones_menu\"\n" +
               "size: 27\n" +
               "title: \"<gold>Marcos de Carreira\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      milestone_info:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:experience_bottle\"\n" +
               "          display-name: \"<green>Progresso e Conquistas\"\n" +
               "          lore:\n" +
               "            - \"<gray>Acompanhe aqui a evolução do seu nível,\"\n" +
               "            - \"<gray>títulos liberados e bônus de eficiência!\"\n" +
               "      back_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"jobs_menu\"\n";
    }

    private String getSpecialistCrateMenuYaml() {
        return "id: \"specialist_crate_menu\"\n" +
               "size: 27\n" +
               "title: \"<light_purple>Caixa de Especialista\"\n\n" +
               "pages:\n" +
               "  main:\n" +
               "    default-page: true\n" +
               "    items:\n" +
               "      crate_info:\n" +
               "        slot: 13\n" +
               "        item:\n" +
               "          material-id: \"minecraft:ender_chest\"\n" +
               "          display-name: \"<light_purple>Caixa de Especialista Pokémon\"\n" +
               "          lore:\n" +
               "            - \"<gray>Abra usando uma <light_purple>Chave de Especialista<gray>!\"\n" +
               "            - \"<gray>Contém itens cosméticos, utilitários, Pokébolas\"\n" +
               "            - \"<gray>e consumíveis de breeding/treino.\"\n" +
               "            - \"\"\n" +
               "            - \"<red>§lATENÇÃO: <gray>Sem itens P2W (Lendários/Shinies).\"\n" +
               "      back_btn:\n" +
               "        slot: 15\n" +
               "        item:\n" +
               "          material-id: \"minecraft:arrow\"\n" +
               "          display-name: \"<red>Voltar\"\n" +
               "        actions:\n" +
               "          - type: \"open_menu\"\n" +
               "            params:\n" +
               "              menu-id: \"jobs_menu\"\n";
    }
}
