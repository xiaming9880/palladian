package ws.palladian.persistence;

import java.util.List;

public class BasicQuery implements Query {

    private String sql;
    private Object[] args;

    public BasicQuery(String sql, List<? extends Object> args) {
        this.sql = sql;
        this.args = args.toArray();
    }

    public BasicQuery(String sql, Object[] args) {
        this.sql = sql;
        this.args = args;
    }

    @Override
    public String getSql() {
        return sql;
    }

    @Override
    public Object[] getArgs() {
        return args;
    }

}
