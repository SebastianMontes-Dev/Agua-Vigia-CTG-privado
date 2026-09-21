package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.GeometriaSector;

import java.util.List;

public interface GeometriaSectoresPort {

    /** Los sectores que tienen geometría sembrada, ordenados por nombre. Los que no la tengan se omiten. */
    List<GeometriaSector> listar();
}
