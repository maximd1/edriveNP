package com.ya0ne.np.service;

import com.ya0ne.core.domain.Country;
import com.ya0ne.core.exceptions.LoadDataException;

public interface NMEAService {
    void prepareStaticData() throws LoadDataException;

    /**
     * Reloads (or loads at startup) current country and all countries bound it
     * @param currentCountry
     */
    void reloadStaticTZData(Country currentCountry);
}
