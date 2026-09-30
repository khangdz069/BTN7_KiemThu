using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai01
    {
        public TestContext TestContext { get; set; }
        [DataSource(
           "Microsoft.VisualStudio.TestTools.DataSource.CSV",
           "|DataDirectory|\\data_csv\\Bai01.csv",
           "Bai01#csv",
           DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai01.csv")]

        [TestMethod]
        public void TestMethod1()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            string ketqua_mongdoi = Convert.ToString(TestContext.DataRow[1]);
            try
            {
                int num = Convert.ToInt32(TestContext.DataRow[0]);
                bool ketqua_thucte = o.primeCheck(num);
                Assert.AreEqual(ketqua_mongdoi.ToLower(), ketqua_thucte.ToString().ToLower());

            }
            catch (Exception)
            {
                Assert.AreEqual("Exception", ketqua_mongdoi);

            }


        }
    }
}
