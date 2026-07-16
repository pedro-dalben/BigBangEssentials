package com.pedrodalben.bigbangessentials.tablist.feature.header;

import com.pedrodalben.bigbangessentials.tablist.config.TablistConfig;
import com.pedrodalben.bigbangessentials.tablist.render.CompiledTabTemplate;
import com.pedrodalben.bigbangessentials.tablist.render.TabTemplateCompiler;

import java.util.ArrayList;
import java.util.List;

public class HeaderFooterDesign {
    private final String id;
    private final int priority;
    private final String condition;
    private final boolean isDefault;
    private final List<CompiledTabTemplate> header;
    private final List<CompiledTabTemplate> footer;

    public HeaderFooterDesign(TablistConfig.DesignSection config) {
        this.id = config.id;
        this.priority = config.priority;
        this.condition = config.condition;
        this.isDefault = config.isDefault;
        
        this.header = new ArrayList<>();
        for (String line : config.header) {
            this.header.add(TabTemplateCompiler.compile(line));
        }
        
        this.footer = new ArrayList<>();
        for (String line : config.footer) {
            this.footer.add(TabTemplateCompiler.compile(line));
        }
    }

    public String getId() { return id; }
    public int getPriority() { return priority; }
    public String getCondition() { return condition; }
    public boolean isDefault() { return isDefault; }
    public List<CompiledTabTemplate> getHeader() { return header; }
    public List<CompiledTabTemplate> getFooter() { return footer; }
}
