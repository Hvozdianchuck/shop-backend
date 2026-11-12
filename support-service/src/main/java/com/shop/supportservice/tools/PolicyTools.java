package com.shop.supportservice.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

public class PolicyTools {

    static final String SEARCH_POLICIES = "searchPolicies";

    private final PolicySearch search;
    private final ToolJournal journal;

    public PolicyTools(PolicySearch search, ToolJournal journal) {
        this.search = search;
        this.journal = journal;
    }

    @Tool(name = SEARCH_POLICIES, description = "Searches the shop policies (delivery, returns, payments, warranty, "
            + "order changes, invoices, privacy) and returns the 3 most relevant sections with their ids.")
    public List<PolicyHit> searchPolicies(@ToolParam(description = "What to look for, in any language") String query) {
        journal.called(SEARCH_POLICIES);
        var hits = search.search(query);
        journal.sectionsReturned(hits);
        return hits;
    }
}
