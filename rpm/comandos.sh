tar -czvf rpm/rpmbuild/SOURCES/FileTalk.tar.gz -C /home/cris/java/javafx/proyectos/FileTalk .
cp -r rpm/rpmbuild ~/
rpmbuild -ba ~/rpmbuild/SPECS/setup.spec

#/home/cris/rpmbuild/RPMS/x86_64/FileTalk-1.0-1.x86_64.rpm
cd ~/rpmbuild/RPMS/x86_64/

sudo dnf install ./FileTalk-1.0-1.x86_64.rpm
sudo rpm -ivh --nodeps FileTalk-1.0-1.x86_64.rpm
sudo rpm -ivh --nodeps FileTalk-1.0-1.fc42.noarch.rpm

sudo firewall-cmd --list-ports
