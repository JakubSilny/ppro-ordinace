package cz.uhk.ordinace.visit.service;

import java.util.List;

import cz.uhk.ordinace.visit.domain.Visit;
import cz.uhk.ordinace.visit.domain.VisitProcedure;

public record VisitDetail(Visit visit, List<VisitProcedure> procedures) {
}
