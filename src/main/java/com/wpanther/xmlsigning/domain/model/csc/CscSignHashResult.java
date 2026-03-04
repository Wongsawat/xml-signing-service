package com.wpanther.xmlsigning.domain.model.csc;

import java.util.List;

public record CscSignHashResult(List<String> signatures, String certificate) {}
