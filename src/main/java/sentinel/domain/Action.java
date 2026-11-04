package sentinel.domain;
import java.util.Map;
public record Action(String name,Map<String,String> arguments){public Action{DomainValidation.requiredText(name,"name");arguments=Map.copyOf(arguments);}}
