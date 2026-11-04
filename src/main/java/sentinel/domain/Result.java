package sentinel.domain;
public record Result(String status,String message){public Result{DomainValidation.requiredText(status,"status");DomainValidation.required(message,"message");}}
