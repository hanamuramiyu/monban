package hanamuramiyu.monban.velocity.sync;

interface StateRevisionStore {
    long load();

    void save(long revision);
}
