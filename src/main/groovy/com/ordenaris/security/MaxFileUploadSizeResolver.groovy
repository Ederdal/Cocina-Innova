package com.ordenaris.security

import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.multipart.support.StandardServletMultipartResolver
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest
import org.springframework.web.multipart.MultipartException

import javax.servlet.http.HttpServletRequest

class MaxFileUploadSizeResolver extends StandardServletMultipartResolver {

    static final String POST_SIZE_EXCEEDED_ERROR = "postSizeExceeded";

    @Override
    MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
        try {
            return super.resolveMultipart(request)
        } catch (MultipartException e) {
            request.setAttribute(POST_SIZE_EXCEEDED_ERROR, true);
            return new DefaultMultipartHttpServletRequest(request, new LinkedMultiValueMap<String, MultipartFile>(), new LinkedHashMap<String, String[]>(), new LinkedHashMap<String, String>());
        }
    }

}