alter table friends drop column temp;

ALTER TABLE friends ADD CONSTRAINT friendsConstraint
UNIQUE (one, two);
