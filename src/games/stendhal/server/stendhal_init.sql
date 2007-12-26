create table if not exists character_stats
  (
  name varchar(32) not null,
  online boolean,
  
  admin int default 0,
  sentence varchar(256),
  age integer,
  level integer,
  outfit varchar(32),
  xp integer,
  money integer,
  
  married varchar(32),
  
  /* Attributes */
  atk integer,
  def integer,
  hp integer,
  karma integer,
  
  /* Equipment */
  head varchar(32),
  armor varchar(32),
  lhand varchar(32),
  rhand varchar(32),
  legs varchar(32),
  feet varchar(32),
  cloak varchar(32),

  timedate timestamp,
  primary key(name)
  )
  TYPE=MYISAM;
  
  
create table if not exists halloffame
  (
  id integer auto_increment not null,
  charname varchar(32) not null,
  fametype char(1) not null,
  points integer not null,

  primary key(id)
  ) 
  TYPE=MYISAM;

  
CREATE TABLE IF NOT EXISTS itemid (
  last_id INTEGER
) TYPE=MYISAM;

CREATE TABLE IF NOT EXISTS itemlog (
  id         INTEGER AUTO_INCREMENT NOT NULL,
  timedate   TIMESTAMP,
  itemid     INTEGER,
  source     VARCHAR(64),
  event      VARCHAR(64),
  param1     VARCHAR(64),
  param2     VARCHAR(64),
  param3     VARCHAR(64),
  param4     VARCHAR(64),
  PRIMARY KEY (id)
) TYPE=MYISAM;