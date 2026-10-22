package org.saltymc.message;

/** User-facing validation failure; domain code contains no formatting or Bukkit calls. */
public final class MessageException extends IllegalArgumentException {
    private final Notice notice;
    public MessageException(String key,Object... pairs) {
        this(Notice.of(key,pairs));
    }
    private MessageException(Notice notice) { super(notice.key()+" "+notice.values()); this.notice=notice; }
    public Notice notice() { return notice; }
}
