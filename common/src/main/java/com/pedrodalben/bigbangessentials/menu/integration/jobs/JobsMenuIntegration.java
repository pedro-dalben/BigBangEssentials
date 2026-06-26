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
            String[] menus = new String[]{"jobs_menu.yml", "job_details_menu.yml"};
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
}
