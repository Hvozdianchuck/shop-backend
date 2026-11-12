package com.shop.supportservice.tools;

import java.util.List;

public interface PolicySearch {

    /**
     * Returns the most relevant policy sections, best first. Section ids look like {@code returns#refund-time}.
     */
    List<PolicyHit> search(String query);
}
