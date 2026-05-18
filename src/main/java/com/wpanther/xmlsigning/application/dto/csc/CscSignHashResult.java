package com.wpanther.xmlsigning.application.dto.csc;

import java.util.List;

public record CscSignHashResult(List<String> signatures, String responseId) {}