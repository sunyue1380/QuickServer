package cn.schoolwow.quickserver.exception;

import cn.schoolwow.quickserver.response.ResponseMeta;

/**
 * 抛出业务异常
 */
public class BusinessException extends Exception {
    public ResponseMeta.HttpStatus httpStatus;
    public String body;

    public BusinessException(ResponseMeta.HttpStatus httpStatus, String body) {
        super(body);
        this.httpStatus = httpStatus;
        this.body = body;
    }
}
